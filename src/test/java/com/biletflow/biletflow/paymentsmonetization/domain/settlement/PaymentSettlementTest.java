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

    private SettlementEvidence refundEvidence(PaymentWork work, BigDecimal original, BigDecimal refunded, boolean merchant) {
        return new SettlementEvidence(
            work.getInvoiceId(),
            "verified",
            BigDecimal.ZERO,
            "KZT",
            merchant,
            SettlementStatus.REFUNDED,
            work.getId().toString(),
            original,
            refunded
        );
    }

    private PaymentWork dispatchedRefund() {
        var work = work();
        work.getPayment().reconcileCharge("verified", now);
        work.recordVerifiedTransaction("verified");
        work.requestTicketRefund("Full refund", now);
        work.recordDispatch(PaymentOperation.REFUND, now);
        return work;
    }

    @Test
    void laterVerifiedFullRefundClearsScopeReviewWithoutRequiringHttpAcknowledgement() {
        var work = dispatchedRefund();
        PaymentSettlement.settle(work, refundEvidence(work, null, null, true), context(), now);
        assertTrue(work.isReviewRequired());
        var effects = PaymentSettlement.settle(
            work,
            refundEvidence(work, work.getAmount(), work.getAmount(), true),
            context(),
            now.plusSeconds(1)
        );
        assertEquals(PaymentStatus.REFUNDED, work.getStatus());
        assertEquals(PaymentOperationState.CONFIRMED, work.getOperationState());
        assertFalse(work.isReviewRequired());
        assertFalse(work.isOperationAcknowledged());
        assertNull(effects.getDispatch());
        assertEquals(1, effects.getOutcomes().size());
        var replay = PaymentSettlement.settle(
            work,
            refundEvidence(work, work.getAmount(), work.getAmount(), true),
            context(),
            now.plusSeconds(2)
        );
        assertTrue(replay.getOutcomes().isEmpty());
        assertNull(replay.getRefundId());
    }

    @Test
    void partialRefundOverridesAnAcknowledgedFullRequestAndDoesNotBookFullRevenueReversal() {
        var work = dispatchedRefund();
        work.acknowledge(work.getOperationId(), PaymentOperation.REFUND);
        var effects = PaymentSettlement.settle(work, refundEvidence(work, work.getAmount(), BigDecimal.TEN, true), context(), now);
        assertTrue(work.isReviewRequired());
        assertEquals(RefundStatus.REQUESTED, work.getPayment().getRefund().orElseThrow().getStatus());
        assertTrue(effects.getOutcomes().isEmpty());
        assertNull(effects.getRefundId());
        assertNull(effects.getDispatch());
    }

    @Test
    void fullRefundTotalsCannotBypassMerchantOrOriginalAmountChecks() {
        for (boolean correctMerchant : new boolean[] { false, true }) {
            var work = dispatchedRefund();
            work.requireReview();
            var original = correctMerchant ? work.getAmount().add(BigDecimal.ONE) : work.getAmount();
            var effects = PaymentSettlement.settle(work, refundEvidence(work, original, work.getAmount(), correctMerchant), context(), now);
            assertTrue(work.isReviewRequired());
            assertEquals(RefundStatus.REQUESTED, work.getPayment().getRefund().orElseThrow().getStatus());
            assertTrue(effects.getOutcomes().isEmpty());
        }
    }

    @Test
    void fullTotalsForAnUnrequestedExternalRefundStillRequireReview() {
        var work = work();
        work.getPayment().reconcileCharge("verified", now);
        work.recordVerifiedTransaction("verified");
        var effects = PaymentSettlement.settle(work, refundEvidence(work, work.getAmount(), work.getAmount(), true), context(), now);
        assertTrue(work.isReviewRequired());
        assertTrue(work.getPayment().getRefund().isEmpty());
        assertTrue(effects.getOutcomes().isEmpty());
        assertNull(effects.getDispatch());
    }

    @Test
    void interactivePaymentsAndRefundsStayFastWhileHistoricalReconciliationBacksOff() {
        var work = work();
        assertEquals(2, work.pollDelaySeconds(now));
        work.getPayment().reconcileCharge("verified", now);
        assertEquals(300, work.pollDelaySeconds(now));
        assertEquals(3600, work.pollDelaySeconds(now.plusSeconds(90000)));
        work.requestTicketRefund("Full refund", now);
        assertEquals(2, work.pollDelaySeconds(now.plusSeconds(1000)));
        work.recordDispatch(PaymentOperation.REFUND, now);
        assertEquals(2, work.pollDelaySeconds(now.plusSeconds(10)));
        assertEquals(30, work.pollDelaySeconds(now.plusSeconds(301)));
        work.requireReview();
        assertEquals(300, work.pollDelaySeconds(now.plusSeconds(301)));
    }

    @Test
    void abandonedOrExpiredUnpaidCheckoutsRemainMonitoredWithoutBusyPolling() {
        var work = work();
        work.abandon();
        assertEquals(30, work.pollDelaySeconds(now));
        assertEquals(300, work.pollDelaySeconds(now.plusSeconds(4000)));
        assertEquals(3600, work.pollDelaySeconds(now.plusSeconds(90000)));
    }
}
