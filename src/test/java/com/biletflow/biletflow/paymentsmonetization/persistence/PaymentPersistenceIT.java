package com.biletflow.biletflow.paymentsmonetization.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.common.events.*;
import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.paymentsmonetization.application.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk.EncryptedJpaPaymentSessionStore;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Requires the project's normal PostgreSQL/Redis/Elasticsearch test containers. No Halyk network calls. */
@IntegrationTest
class PaymentPersistenceIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entities;

    @Autowired
    TransactionLocks locks;

    @Autowired
    JsonCodec json;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    PaymentWorkRepository works;

    @Autowired
    TicketRepository tickets;

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    @Test
    void producerRollbackAlsoRollsBackTheOutbox() {
        var bus = new JdbcEventBus(jdbc, json, Clock.systemUTC(), manager, List.of());
        UUID id = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () ->
            tx().executeWithoutResult(s -> {
                bus.publish(new IntegrationEvent(id, "test", id, Map.of()));
                throw new IllegalStateException("rollback");
            })
        );
        assertEquals(0, jdbc.queryForObject("select count(*) from common_outbox where id=?", Integer.class, id));
    }

    @Test
    void handlerJpaEffectsAndAckRollbackTogetherAndThenRetry() {
        UUID event = UUID.randomUUID();
        var ticket = Ticket.issue(
            TicketTypeId.generate(),
            event,
            new com.biletflow.biletflow.ticketinventory.domain.common.OrderId(UUID.randomUUID()),
            new AttendeeEmail("test@example.com"),
            null,
            null,
            Instant.now()
        );
        AtomicBoolean fail = new AtomicBoolean(true);
        String type = "test." + UUID.randomUUID();
        IntegrationEventHandler handler = new IntegrationEventHandler() {
            public String eventType() {
                return type;
            }

            public void handle(IntegrationEvent ignored) {
                tickets.save(ticket);
                if (fail.get()) throw new IllegalStateException("Simulated consumer crash");
            }
        };
        var bus = new JdbcEventBus(jdbc, json, Clock.systemUTC(), manager, List.of(handler));
        UUID id = UUID.randomUUID();
        tx().executeWithoutResult(s -> bus.publish(new IntegrationEvent(id, type, event, Map.of())));
        bus.dispatch();
        assertTrue(tickets.findById(ticket.getId()).isEmpty());
        assertNull(jdbc.queryForObject("select delivered_at from common_outbox where id=?", Timestamp.class, id));
        fail.set(false);
        jdbc.update("update common_outbox set available_at=? where id=?", Timestamp.from(Instant.EPOCH), id);
        bus.dispatch();
        assertTrue(tickets.findById(ticket.getId()).isPresent());
        assertNotNull(jdbc.queryForObject("select delivered_at from common_outbox where id=?", Timestamp.class, id));
        jdbc.update("delete from common_outbox where id=?", id);
        tx().executeWithoutResult(s -> jdbc.update("delete from ticket_inventory_ticket where id=?", ticket.getId().value()));
    }

    @Test
    void onlyOneWorkerAcquiresAReconciliationLease() throws Exception {
        var w = work();
        tx().executeWithoutResult(s -> works.insert(w));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            Callable<Boolean> take = () -> {
                gate.await();
                return tx().execute(s -> works.lease(w.id, Instant.now(), Instant.now().plusSeconds(60)));
            };
            var first = pool.submit(take);
            var second = pool.submit(take);
            gate.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            jdbc.update("delete from pm_payment_work where id=?", w.id);
        }
    }

    @Test
    void aggregateAndEveryRefundAttemptSurviveRoundTrip() {
        var w = work();
        tx().executeWithoutResult(s -> works.insert(w));
        w.payment.succeed(UUID.randomUUID().toString(), Instant.now());
        var first = w.payment.requestRefund("First", Instant.now());
        w.payment.failRefund("Confirmed rejected", Instant.now());
        tx().executeWithoutResult(s -> works.save(w));
        var second = w.payment.requestRefund("Retry", Instant.now());
        tx().executeWithoutResult(s -> works.save(w));
        var loaded = works.find(w.id).orElseThrow();
        assertEquals(w.amount, loaded.amount);
        assertEquals(second.getId(), loaded.payment.getRefund().orElseThrow().getId());
        assertEquals(2, jdbc.queryForObject("select count(*) from pm_refund_history where payment_id=?", Integer.class, w.id));
        assertEquals("FAILED", jdbc.queryForObject("select status from pm_refund_history where id=?", String.class, first.getId().value()));
        tx().executeWithoutResult(status -> {
            jdbc.update("delete from pm_refund_history where payment_id=?", w.id);
            jdbc.update("delete from pm_payment_work where id=?", w.id);
        });
    }

    @Test
    void lockedTicketRefreshesAnEarlierUnprotectedRead() throws Exception {
        var ticket = Ticket.issue(
            TicketTypeId.generate(),
            UUID.randomUUID(),
            new com.biletflow.biletflow.ticketinventory.domain.common.OrderId(UUID.randomUUID()),
            new AttendeeEmail("test@example.com"),
            null,
            null,
            Instant.now()
        );
        tx().executeWithoutResult(status -> tickets.save(ticket));
        var loaded = new CountDownLatch(1);
        var changed = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var reader = pool.submit(() ->
                tx().execute(status -> {
                    tickets.findById(ticket.getId()).orElseThrow();
                    loaded.countDown();
                    try {
                        if (!changed.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return tickets.findByTicketCodeForUpdate(ticket.getTicketCode()).orElseThrow().getStatus();
                })
            );
            assertTrue(loaded.await(10, TimeUnit.SECONDS));
            tx().executeWithoutResult(status -> {
                var current = tickets.findByTicketCodeForUpdate(ticket.getTicketCode()).orElseThrow();
                current.checkIn();
                tickets.save(current);
            });
            changed.countDown();
            assertEquals(TicketStatus.CHECKED_IN, reader.get(10, TimeUnit.SECONDS));
        } finally {
            changed.countDown();
            pool.shutdownNow();
            tx().executeWithoutResult(status -> jdbc.update("delete from ticket_inventory_ticket where id=?", ticket.getId().value()));
        }
    }

    @Test
    void relationalPaymentStateIsQueryableAndLedgerIsIdempotent() {
        var work = work();
        tx().executeWithoutResult(status -> works.insert(work));
        try {
            tx().executeWithoutResult(status -> {
                work.payment.succeed(UUID.randomUUID().toString(), Instant.now());
                works.save(work);
                entities.flush();
                entities.clear();
                var stored = entities.find(PaymentWorkJpaEntity.class, work.id);
                assertEquals(work.amount, stored.amount);
                assertEquals("SUCCEEDED", stored.status);
                var ledger = new JpaPaymentLedger(entities, key -> {}, Clock.systemUTC());
                ledger.charge(work);
                ledger.charge(work);
                entities.flush();
            });
            assertEquals(1, jdbc.queryForObject("select count(*) from pm_ledger where payment_id=?", Integer.class, work.id));
            assertEquals(
                0,
                jdbc.queryForObject(
                    "select count(*) from information_schema.columns where table_schema=current_schema() and table_name='pm_payment_work' and column_name='snapshot'",
                    Integer.class
                )
            );
        } finally {
            tx().executeWithoutResult(status -> {
                jdbc.update("delete from pm_ledger where payment_id=?", work.id);
                jdbc.update("delete from pm_payment_work where id=?", work.id);
            });
        }
    }

    @Test
    void refreshAfterLockDoesNotReuseStateReadBeforeAnotherTransactionCommits() throws Exception {
        var work = work();
        tx().executeWithoutResult(status -> works.insert(work));
        var loaded = new CountDownLatch(1);
        var changed = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var reader = pool.submit(() ->
                tx().execute(status -> {
                    works.find(work.id).orElseThrow();
                    loaded.countDown();
                    try {
                        if (!changed.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return works.find(work.id).orElseThrow().status();
                })
            );
            assertTrue(loaded.await(10, TimeUnit.SECONDS));
            tx().executeWithoutResult(status -> {
                var latest = works.find(work.id).orElseThrow();
                latest.payment.succeed("verified", Instant.now());
                works.save(latest);
            });
            changed.countDown();
            assertEquals("SUCCEEDED", reader.get(10, TimeUnit.SECONDS));
        } finally {
            changed.countDown();
            pool.shutdownNow();
            jdbc.update("delete from pm_payment_work where id=?", work.id);
        }
    }

    @Test
    void encryptedSessionClaimsCannotPublishOrDeleteASupersedingClaim() {
        var work = work();
        tx().executeWithoutResult(status -> works.insert(work));
        var store = new EncryptedJpaPaymentSessionStore(entities, json, locks, manager, Base64.getEncoder().encodeToString(new byte[32]));
        Instant now = Instant.now();
        UUID first = UUID.randomUUID(),
            second = UUID.randomUUID();
        var form = new PaymentGateway.Session(Map.of(), "sandbox-script", Map.of("auth", Map.of("expires_in", 7200)));
        try {
            assertNull(store.claim(work.id, first, now));
            assertThrows(IllegalStateException.class, () -> store.claim(work.id, second, now.plusSeconds(1)));
            assertNull(store.claim(work.id, second, now.plusSeconds(61)));
            assertThrows(IllegalStateException.class, () -> store.complete(work.id, first, form, work.deadline, now.plusSeconds(62)));
            store.release(work.id, first);
            assertEquals(second, jdbc.queryForObject("select claim_id from pm_payment_session where payment_id=?", UUID.class, work.id));
            store.complete(work.id, second, form, work.deadline, now.plusSeconds(62));
            store.release(work.id, second);
            assertEquals(form, store.claim(work.id, UUID.randomUUID(), now.plusSeconds(63)));
            String payload = jdbc.queryForObject(
                "select encrypted_payload from pm_payment_session where payment_id=?",
                String.class,
                work.id
            );
            assertFalse(payload.contains("sandbox-script"));
        } finally {
            tx().executeWithoutResult(status -> {
                jdbc.update("delete from pm_payment_session where payment_id=?", work.id);
                jdbc.update("delete from pm_payment_work where id=?", work.id);
            });
        }
    }

    private PaymentWork work() {
        var w = new PaymentWork();
        w.purpose = "TICKET";
        w.orderId = UUID.randomUUID();
        w.eventId = UUID.randomUUID();
        w.payerId = 1;
        w.organizerId = 7;
        w.amount = new BigDecimal("1000.00");
        w.fee = new BigDecimal("30.00");
        w.invoiceId = works.nextInvoice();
        w.callbackSecret = "test-secret";
        w.deadline = Instant.now().plusSeconds(600);
        w.payment = TicketOrderPayment.initiate(w.orderId, w.eventId, w.organizerId, Money.kzt(w.amount));
        w.id = w.payment.getId().value();
        return w;
    }
}
