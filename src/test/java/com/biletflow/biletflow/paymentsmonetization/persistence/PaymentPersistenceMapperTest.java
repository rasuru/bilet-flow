package com.biletflow.biletflow.paymentsmonetization.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
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
        work.payment.reconcileCharge("transaction", now);
        var refund = work.payment.requestRefund("Full refund", now);
        work.payment.failRefund("Confirmed rejected", now);
        work.operationId = refund.getId().value();
        work.operation = "refund";
        work.operationState = "UNKNOWN";
        work.operationAt = now;
        work.operationAcknowledged = true;
        work.reviewRequired = true;
        work.bankTransactionId = "transaction";
        var mapper = new PaymentPersistenceMapper();
        var row = new PaymentWorkJpaEntity();
        var attempt = new RefundJpaEntity();
        mapper.update(work, row);
        mapper.update(refund, work.id, attempt);
        var loaded = mapper.domain(row, attempt);
        assertEquals(work.id, loaded.id);
        assertEquals(work.amount, loaded.amount);
        assertEquals(work.fee, loaded.fee);
        assertEquals(work.operationId, loaded.operationId);
        assertEquals("UNKNOWN", loaded.operationState);
        assertTrue(loaded.operationAcknowledged);
        assertTrue(loaded.reviewRequired);
        assertEquals("transaction", loaded.bankTransactionId);
        assertEquals(refund.getStatus(), loaded.payment.getRefund().orElseThrow().getStatus());
        assertEquals(refund.getFailureReason(), loaded.payment.getRefund().orElseThrow().getFailureReason());
    }

    @Test
    void compensatingActivationRefundSurvivesMapping() {
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        var work = new PaymentTerms(new BigDecimal("1000"), BigDecimal.ZERO).activation(UUID.randomUUID(), 7, now, "123456", "secret");
        work.activationPayment.reconcileCharge("transaction", now);
        work.activationPayment.requestCompensatingRefund();
        var mapper = new PaymentPersistenceMapper();
        var row = new PaymentWorkJpaEntity();
        mapper.update(work, row);
        var loaded = mapper.domain(row, null);
        assertFalse(loaded.isTicket());
        assertEquals("REFUND_REQUESTED", loaded.status());
        assertEquals("transaction", loaded.activationPayment.getProviderReference());
    }
}
