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
            work.getInvoiceId(),
            UUID.randomUUID().toString(),
            work.getAmount(),
            "KZT",
            merchant,
            status,
            work.getId().toString()
        );
    }

    @Test
    void authorizationCommitsIntentWithoutRecognizingRevenue() {
        var work = work();
        var effects = PaymentSettlement.settle(work, evidence(work, SettlementStatus.AUTHORIZED, true), context(), now);
        assertEquals(PaymentOperation.CAPTURE, effects.getDispatch().operation());
        assertEquals(PaymentOperationState.SENT, work.getOperationState());
        assertFalse(work.hasBeenCharged());
        assertFalse(effects.isCharge());
        assertTrue(effects.getOutcomes().isEmpty());
    }

    @Test
    void wrongMerchantCannotChangeFinancialState() {
        var work = work();
        var effects = PaymentSettlement.settle(work, evidence(work, SettlementStatus.CAPTURED, false), context(), now);
        assertTrue(work.isReviewRequired());
        assertFalse(work.hasBeenCharged());
        assertNull(effects.getDispatch());
        assertFalse(effects.isCharge());
    }

    @Test
    void lateCaptureRequestsCompensationBeforeAnyRefundDispatch() {
        var work = work();
        var effects = PaymentSettlement.settle(work, evidence(work, SettlementStatus.CAPTURED, true), context(), now.plusSeconds(61));
        assertTrue(work.hasBeenCharged());
        assertEquals(RefundStatus.REQUESTED, work.getPayment().getRefund().orElseThrow().getStatus());
        assertTrue(effects.isCharge());
        assertNull(effects.getDispatch());
    }

    @Test
    void pricingRoundsHalfUpToKztPrecision() {
        assertEquals(new BigDecimal("3.71"), work().getFee());
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
        work.getPayment().reconcileCharge("verified", now);
        work.requestTicketRefund("Full refund", now);
        UUID current = work.recordDispatch(PaymentOperation.REFUND, now);
        work.acknowledge(UUID.randomUUID(), PaymentOperation.REFUND);
        assertFalse(work.isOperationAcknowledged());
        work.acknowledge(current, PaymentOperation.CAPTURE);
        assertFalse(work.isOperationAcknowledged());
        work.acknowledge(current, PaymentOperation.REFUND);
        assertTrue(work.isOperationAcknowledged());
    }

    @Test
    void transitionEffectsCannotBeMutatedByApplicationCallers() {
        var work = work();
        var effects = PaymentSettlement.settle(work, evidence(work, SettlementStatus.CAPTURED, true), context(), now);
        assertThrows(UnsupportedOperationException.class, () -> effects.getOutcomes().clear());
        assertThrows(UnsupportedOperationException.class, () -> effects.getAudit().clear());
    }
}
