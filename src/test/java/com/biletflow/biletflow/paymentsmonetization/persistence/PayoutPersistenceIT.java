package com.biletflow.biletflow.paymentsmonetization.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.application.*;
import com.biletflow.biletflow.paymentsmonetization.application.*;
import com.biletflow.biletflow.paymentsmonetization.application.command.RequestPayoutCommand;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.InsufficientBalanceException;
import com.biletflow.biletflow.paymentsmonetization.domain.payout.*;
import com.biletflow.biletflow.paymentsmonetization.domain.revenue.OrganizerBalanceRepository;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Exercises PostgreSQL locks, JPQL projections, durable reservations, rollback and email leases. */
@IntegrationTest
class PayoutPersistenceIT {

    @Autowired
    PayoutService service;

    @Autowired
    PayoutRepository payouts;

    @Autowired
    OrganizerBalanceRepository balances;

    @Autowired
    PaymentWorkRepository works;

    @Autowired
    PaymentLedger ledger;

    @Autowired
    PayoutNoticeStore notices;

    @Autowired
    TransactionLocks locks;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    EntityManager entities;

    private final long organizer = ThreadLocalRandom.current().nextLong(100000, Long.MAX_VALUE);
    private final Instant now = Instant.now();

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    private RequestPayoutCommand request(UUID id, String amount) {
        return new RequestPayoutCommand(organizer, id, new BigDecimal(amount), true);
    }

    private PaymentWork seedCharge() {
        return tx().execute(status -> {
            var work = new PaymentTerms(BigDecimal.TEN, new BigDecimal("0.03")).ticket(
                UUID.randomUUID(),
                UUID.randomUUID(),
                organizer,
                organizer,
                new BigDecimal("1000.00"),
                now.plusSeconds(600),
                now,
                works.nextInvoice(),
                "test-secret"
            );
            work.getPayment().reconcileCharge("sandbox-charge", now);
            works.insert(work);
            ledger.charge(work);
            ledger.charge(work);
            return work;
        });
    }

    @AfterEach
    void cleanup() {
        tx().executeWithoutResult(status -> {
            entities
                .createQuery(
                    "delete FinancialAuditJpaEntity a where a.actor = :organizer or a.aggregateId in (select p.id from PayoutJpaEntity p where p.organizerId = :organizer)"
                )
                .setParameter("organizer", organizer)
                .executeUpdate();
            entities
                .createQuery("delete PayoutJpaEntity p where p.organizerId = :organizer")
                .setParameter("organizer", organizer)
                .executeUpdate();
            entities
                .createQuery("delete LedgerEntryJpaEntity l where l.organizerId = :organizer")
                .setParameter("organizer", organizer)
                .executeUpdate();
            entities
                .createQuery("update PaymentWorkJpaEntity w set w.currentRefundId = null where w.organizerId = :organizer")
                .setParameter("organizer", organizer)
                .executeUpdate();
            entities
                .createQuery(
                    "delete RefundJpaEntity r where r.paymentId in (select w.id from PaymentWorkJpaEntity w where w.organizerId = :organizer)"
                )
                .setParameter("organizer", organizer)
                .executeUpdate();
            entities
                .createQuery("delete PaymentWorkJpaEntity w where w.organizerId = :organizer")
                .setParameter("organizer", organizer)
                .executeUpdate();
        });
    }

    @Test
    void historicalRevenueCountsAndConcurrentRequestsCannotOverspend() throws Exception {
        seedCharge();
        assertEquals(new BigDecimal("970.00"), service.balance(organizer).availableKzt());
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> request = () -> {
            start.await();
            try {
                service.request(request(UUID.randomUUID(), "970"));
                return true;
            } catch (InsufficientBalanceException expected) {
                return false;
            }
        };
        try {
            var a = pool.submit(request);
            var b = pool.submit(request);
            start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, service.list(organizer, 0, 50).size());
        assertEquals(new BigDecimal("970.00"), service.balance(organizer).pendingPayoutKzt());
        assertEquals(new BigDecimal("0.00"), service.balance(organizer).availableKzt());
    }

    @Test
    void concurrentReplayAndProcessingKeepOnePayoutOneAuditOutcomeAndOneNotice() throws Exception {
        seedCharge();
        UUID id = UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<UUID> request = () -> {
            start.await();
            return service.request(request(id, "500")).id();
        };
        try {
            var a = pool.submit(request);
            var b = pool.submit(request);
            start.countDown();
            assertEquals(id, a.get(10, TimeUnit.SECONDS));
            assertEquals(id, b.get(10, TimeUnit.SECONDS));
            var first = pool.submit(() -> service.process(new PayoutId(id), true));
            var second = pool.submit(() -> service.process(new PayoutId(id), true));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals("COMPLETED", service.request(request(id, "500")).status());
        assertEquals(new BigDecimal("500.00"), service.balance(organizer).paidOutKzt());
        Long auditCount = tx().execute(s ->
            entities
                .createQuery("select count(a) from FinancialAuditJpaEntity a where a.aggregateId = :id and a.action = :action", Long.class)
                .setParameter("id", id)
                .setParameter("action", "PAYOUT_COMPLETED_SIMULATED")
                .getSingleResult()
        );
        assertNotNull(auditCount);
        assertEquals(1L, auditCount.longValue());
        var claim = notices.claim(now.plusSeconds(60)).orElseThrow();
        assertEquals(id, claim.payout().id());
        assertTrue(notices.claim(now.plusSeconds(61)).isEmpty());
        var recovered = notices.claim(now.plusSeconds(180)).orElseThrow();
        notices.complete(claim, now.plusSeconds(181)); // stale acknowledgement cannot consume recovered claim
        notices.retry(recovered, now.plusSeconds(182));
        assertTrue(notices.claim(now.plusSeconds(211)).isEmpty());
        var retry = notices.claim(now.plusSeconds(212)).orElseThrow();
        notices.complete(retry, now.plusSeconds(213));
        service.process(new PayoutId(id), true);
        assertTrue(notices.claim(now.plusSeconds(1000)).isEmpty());
    }

    @Test
    void requestAndCompletionRollbackWithTheirAuditAndNotificationState() {
        seedCharge();
        UUID id = UUID.randomUUID();
        var audit = mock(FinancialAudit.class);
        var subject = new PayoutService(payouts, balances, locks, audit, Clock.fixed(now, ZoneOffset.UTC));
        doThrow(new IllegalStateException("audit failure")).when(audit).record("payments", id, organizer, "PAYOUT_REQUESTED_SIMULATED");
        assertThrows(IllegalStateException.class, () -> tx().executeWithoutResult(s -> subject.request(request(id, "970"))));
        assertTrue(payouts.findById(new PayoutId(id)).isEmpty());
        assertEquals(new BigDecimal("970.00"), service.balance(organizer).availableKzt());
        service.request(request(id, "970"));
        reset(audit);
        doThrow(new IllegalStateException("audit failure")).when(audit).record("payments", id, null, "PAYOUT_COMPLETED_SIMULATED");
        assertThrows(IllegalStateException.class, () -> tx().executeWithoutResult(s -> subject.process(new PayoutId(id), true)));
        assertEquals("REQUESTED", service.get(id, organizer).status());
        assertTrue(notices.claim(now.plusSeconds(60)).isEmpty());
        service.process(new PayoutId(id), false);
        assertEquals(new BigDecimal("970.00"), service.balance(organizer).availableKzt());
        assertEquals("FAILED", notices.claim(now.plusSeconds(60)).orElseThrow().payout().status());
    }

    @Test
    void pendingRefundBlocksPayoutAndConfirmedRefundAfterPayoutBecomesDeficit() {
        var work = seedCharge();
        UUID id = UUID.randomUUID();
        service.request(request(id, "970"));
        service.process(new PayoutId(id), true);
        tx().executeWithoutResult(s -> {
            locks.lock("payment:" + work.getId());
            locks.lock("organizer-funds:" + organizer);
            var latest = works.find(work.getId()).orElseThrow();
            latest.requestTicketRefund("test refund", now);
            works.save(latest);
        });
        assertTrue(service.balance(organizer).reconciliationPending());
        tx().executeWithoutResult(s -> {
            var latest = works.find(work.getId()).orElseThrow();
            var refundId = latest.getPayment().getRefund().orElseThrow().getId().value();
            latest.getPayment().completeRefund("bank-refund", now.plusSeconds(1));
            works.save(latest);
            ledger.refund(latest, refundId);
            ledger.refund(latest, refundId);
        });
        assertFalse(service.balance(organizer).reconciliationPending());
        assertEquals(new BigDecimal("1000.00"), service.balance(organizer).deficitKzt());
        assertEquals(new BigDecimal("0.00"), service.balance(organizer).availableKzt());
        assertThrows(InsufficientBalanceException.class, () -> service.request(request(UUID.randomUUID(), "0.01")));
    }
}
