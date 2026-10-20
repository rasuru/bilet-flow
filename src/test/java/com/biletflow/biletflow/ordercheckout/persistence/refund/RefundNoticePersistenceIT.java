package com.biletflow.biletflow.ordercheckout.persistence.refund;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.application.*;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.eventmanagement.ohs.dto.TicketEventDetailsView;
import com.biletflow.biletflow.ordercheckout.application.*;
import com.biletflow.biletflow.ordercheckout.application.refund.*;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderRepository;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutJpaEntity;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutProcessJpaEntity;
import com.biletflow.biletflow.ordercheckout.persistence.refund.entity.RefundNoticeJpaEntity;
import com.biletflow.biletflow.paymentsmonetization.ohs.*;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@TestPropertySource(properties = "biletflow.checkout.refund-mail-enabled=false")
class RefundNoticePersistenceIT {

    @Autowired
    RefundNoticeStore notices;

    @Autowired
    CheckoutSessionRepository checkouts;

    @Autowired
    CheckoutProcessRepository processes;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    EntityManager entities;

    private final Instant now = Instant.parse("2026-10-20T12:00:00Z");
    private final UUID orderId = UUID.randomUUID();
    private final UUID refundId = UUID.randomUUID();

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    private RefundNotice notice() {
        return new RefundNotice(refundId, orderId, UUID.randomUUID(), 42, "Refunded event", new BigDecimal("1000.00"), now);
    }

    @AfterEach
    void cleanup() {
        tx().executeWithoutResult(status -> {
            for (Class<?> type : List.of(RefundNoticeJpaEntity.class, CheckoutProcessJpaEntity.class, CheckoutJpaEntity.class)) {
                var row = entities.find(type, type == RefundNoticeJpaEntity.class ? refundId : orderId);
                if (row != null) entities.remove(row);
            }
        });
    }

    @Test
    void refundStateAndReceiptRollbackTogetherAndDuplicateOutcomeDoesNotResetSentReceipt() {
        var eventId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var checkout = new CheckoutSession(new CheckoutSessionId(orderId), 42, eventId, InventoryMode.GENERAL_ADMISSION);
        checkout.addItem(new GeneralAdmissionCheckoutItem(UUID.randomUUID(), Money.kzt(1000)));
        checkout.awaitPayment();
        var process = new CheckoutProcess(
            orderId,
            42,
            eventId,
            "fingerprint",
            now.plusSeconds(600),
            List.of(),
            List.of(),
            paymentId,
            CheckoutRefundStatus.PENDING,
            refundId
        );
        tx().executeWithoutResult(status -> {
            checkouts.save(checkout);
            processes.save(process);
        });
        var events = mock(EventManagementOhs.class);
        when(events.ticketDetails(eventId, null)).thenReturn(
            new TicketEventDetailsView("Event", now, now.plusSeconds(3600), "Venue", "Address", "", false)
        );
        var audit = mock(FinancialAudit.class);
        var service = new CheckoutService(
            checkouts,
            mock(OrderRepository.class),
            processes,
            mock(CheckoutInventoryOhs.class),
            mock(PaymentsOhs.class),
            events,
            mock(TransactionLocks.class),
            audit,
            mock(CheckoutFingerprint.class),
            Clock.fixed(now, ZoneOffset.UTC),
            notices
        );
        var outcome = new PaymentOutcome(paymentId, orderId, "REFUNDED", refundId);
        doThrow(new IllegalStateException("Rollback after receipt enqueue"))
            .when(audit)
            .record("ordercheckout", orderId, null, "ORDER_REFUND_COMPLETED");
        assertThrows(IllegalStateException.class, () -> tx().executeWithoutResult(status -> service.onOutcome(outcome)));
        assertNull(tx().execute(status -> entities.find(RefundNoticeJpaEntity.class, refundId)));
        assertEquals(CheckoutRefundStatus.PENDING, processes.find(orderId).orElseThrow().refundState());
        assertEquals(CheckoutSessionStatus.AWAITING_PAYMENT, checkouts.findById(new CheckoutSessionId(orderId)).orElseThrow().getStatus());
        reset(audit);
        tx().executeWithoutResult(status -> service.onOutcome(outcome));
        assertEquals(CheckoutRefundStatus.REFUNDED, processes.find(orderId).orElseThrow().refundState());
        var claim = notices.claim(now).orElseThrow();
        assertEquals(refundId, claim.notice().id());
        notices.complete(claim, now.plusSeconds(1));
        tx().executeWithoutResult(status -> service.onOutcome(outcome));
        tx().executeWithoutResult(status -> notices.enqueue(claim.notice()));
        assertTrue(notices.claim(now.plusSeconds(1000)).isEmpty());
    }

    @Test
    void concurrentWorkersAndRestartPreserveOneClaimAndRejectStaleAcknowledgement() throws Exception {
        tx().executeWithoutResult(status -> notices.enqueue(notice()));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Optional<RefundNoticeStore.Claim>> claim = () -> {
                start.await();
                return notices.claim(now);
            };
            var first = pool.submit(claim);
            var second = pool.submit(claim);
            start.countDown();
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(a.isPresent(), b.isPresent());
            var old = a.orElseGet(b::orElseThrow);
            var recovered = notices.claim(now.plusSeconds(120)).orElseThrow();
            notices.complete(old, now.plusSeconds(121));
            notices.retry(recovered, now.plusSeconds(122));
            assertTrue(notices.claim(now.plusSeconds(181)).isEmpty());
            var retry = notices.claim(now.plusSeconds(182)).orElseThrow();
            assertEquals(old.notice(), retry.notice());
            notices.complete(retry, now.plusSeconds(183));
            assertTrue(notices.claim(now.plusSeconds(1000)).isEmpty());
        } finally {
            pool.shutdownNow();
        }
    }
}
