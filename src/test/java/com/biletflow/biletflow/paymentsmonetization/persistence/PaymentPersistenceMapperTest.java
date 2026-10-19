package com.biletflow.biletflow.paymentsmonetization.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import com.biletflow.biletflow.paymentsmonetization.persistence.payment.PaymentPersistenceMapper;
import com.biletflow.biletflow.paymentsmonetization.persistence.payment.PaymentPersistenceMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentPersistenceMapperTest {

    @Test
    void uncertainOperationAndFailedRefundSurviveMapping() {
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        var terms = new PaymentTerms(new BigDecimal("1000"), new BigDecimal("0.03"));
        var work = terms.ticket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            7,
            new BigDecimal("1000.00"),
            now.plusSeconds(600),
            now,
            "123456",
            "secret"
        );
        work.getPayment().reconcileCharge("transaction", now);
        var refund = work.getPayment().requestRefund("Full refund", now);
        work.getPayment().failRefund("Confirmed rejected", now);
        work.recordDispatch(PaymentOperation.REFUND, now);
        work.acknowledge(work.getOperationId(), PaymentOperation.REFUND);
        work.markOperationUnknown();
        work.requireReview();
        work.recordVerifiedTransaction("transaction");
        var mapper = new PaymentPersistenceMapper();
        var row = mapper.toNewEntity(work);
        var attempt = mapper.toNewEntity(refund, work.getId());
        var loaded = mapper.toDomain(row, attempt);
        assertEquals(work.getId(), loaded.getId());
        assertEquals(work.getAmount(), loaded.getAmount());
        assertEquals(work.getFee(), loaded.getFee());
        assertEquals(work.getOperationId(), loaded.getOperationId());
        assertEquals(PaymentOperationState.UNKNOWN, loaded.getOperationState());
        assertTrue(loaded.isOperationAcknowledged());
        assertTrue(loaded.isReviewRequired());
        assertEquals("transaction", loaded.getBankTransactionId());
        assertEquals(refund.getStatus(), loaded.getPayment().getRefund().orElseThrow().getStatus());
        assertEquals(refund.getFailureReason(), loaded.getPayment().getRefund().orElseThrow().getFailureReason());
    }

    @Test
    void compensatingActivationRefundSurvivesMapping() {
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        var work = new PaymentTerms(new BigDecimal("1000"), BigDecimal.ZERO).activation(UUID.randomUUID(), 7, now, "123456", "secret");
        work.getActivationPayment().reconcileCharge("transaction", now);
        work.getActivationPayment().requestCompensatingRefund();
        var mapper = new PaymentPersistenceMapper();
        var row = mapper.toNewEntity(work);
        var loaded = mapper.toDomain(row, null);
        assertFalse(loaded.isTicket());
        assertEquals("REFUND_REQUESTED", loaded.getStatus().name());
        assertEquals("transaction", loaded.getActivationPayment().getProviderReference());
    }

    @Test
    void updateCannotReplaceAnotherPaymentsIdentity() {
        var now = Instant.now();
        var terms = new PaymentTerms(new BigDecimal("1000"), BigDecimal.ZERO);
        var first = terms.activation(UUID.randomUUID(), 7, now, "123456", "secret");
        var second = terms.activation(UUID.randomUUID(), 7, now, "123457", "secret");
        var mapper = new PaymentPersistenceMapper();
        var entity = mapper.toNewEntity(first);
        assertThrows(IllegalArgumentException.class, () -> mapper.updateEntity(second, entity));
        assertEquals(first.getId(), entity.getId());
        assertEquals(first.getEventId(), entity.getEventId());
    }
}
