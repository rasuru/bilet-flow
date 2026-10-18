package com.biletflow.biletflow.paymentsmonetization.domain.settlement;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentSettlementTest {

    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private final PaymentTerms terms = new PaymentTerms(new BigDecimal("1000"), new BigDecimal("0.03"));

    private PaymentWork work() {
        return terms.ticket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            7,
            new BigDecimal("123.50"),
            now.plusSeconds(60),
            now,
            "123456",
            "secret"
        );
    }

    private PaymentSettlement.Context context() {
        return new PaymentSettlement.Context(new PaymentSettlement.EventFacts(true, false, 7, now.plusSeconds(3600)), null, null, false);
    }

    private SettlementEvidence evidence(PaymentWork work, SettlementStatus status, boolean merchant) {
        return new SettlementEvidence(
            work.invoiceId,
            UUID.randomUUID().toString(),
            work.amount,
            "KZT",
            merchant,
            status,
            work.id.toString()
        );
    }

    @Test
    void authorizationCommitsIntentWithoutRecognizingRevenue() {
        var work = work();
        var effects = PaymentSettlement.settle(work, evidence(work, SettlementStatus.AUTHORIZED, true), context(), now);
        assertEquals("charge", effects.dispatch.operation());
        assertEquals("SENT", work.operationState);
        assertFalse(work.charged());
        assertFalse(effects.charge);
        assertTrue(effects.outcomes.isEmpty());
    }

    @Test
    void wrongMerchantCannotChangeFinancialState() {
        var work = work();
        var effects = PaymentSettlement.settle(work, evidence(work, SettlementStatus.CAPTURED, false), context(), now);
        assertTrue(work.reviewRequired);
        assertFalse(work.charged());
        assertNull(effects.dispatch);
        assertFalse(effects.charge);
    }

    @Test
    void lateCaptureRequestsCompensationBeforeAnyRefundDispatch() {
        var work = work();
        var effects = PaymentSettlement.settle(work, evidence(work, SettlementStatus.CAPTURED, true), context(), now.plusSeconds(61));
        assertTrue(work.charged());
        assertEquals(RefundStatus.REQUESTED, work.payment.getRefund().orElseThrow().getStatus());
        assertTrue(effects.charge);
        assertNull(effects.dispatch);
    }

    @Test
    void pricingRoundsHalfUpToKztPrecision() {
        assertEquals(new BigDecimal("3.71"), work().fee);
        assertThrows(IllegalArgumentException.class, () -> new PaymentTerms(new BigDecimal("1000.001"), BigDecimal.ZERO));
    }

    @Test
    void expiredCheckoutCannotCreatePayment() {
        assertThrows(IllegalStateException.class, () ->
            terms.ticket(UUID.randomUUID(), UUID.randomUUID(), 1, 7, BigDecimal.TEN, now, now, "123456", "secret")
        );
    }

    @Test
    void acknowledgingSupersededOperationDoesNotAcknowledgeReplacement() {
        var work = work();
        UUID current = UUID.randomUUID();
        work.operationId = current;
        work.operation = "refund";
        work.operationState = "SENT";
        work.acknowledge(UUID.randomUUID(), "refund");
        assertFalse(work.operationAcknowledged);
        work.acknowledge(current, "charge");
        assertFalse(work.operationAcknowledged);
        work.acknowledge(current, "refund");
        assertTrue(work.operationAcknowledged);
    }
}
