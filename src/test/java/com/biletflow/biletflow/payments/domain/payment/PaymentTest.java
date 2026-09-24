package com.biletflow.biletflow.payments.domain.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.payments.domain.exceptions.InvalidPaymentStateException;
import com.biletflow.biletflow.payments.domain.exceptions.RefundExceedsPaymentException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final Long ORGANIZER_ID = 7L;

    // --- Initiation ---

    @Test
    void initiatedPaymentStartsPendingWithNoRefunds() {
        Payment payment = Payment.initiateForOrder(UUID.randomUUID(), EVENT_ID, ORGANIZER_ID, Money.kzt(10000));

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(PaymentPurpose.TICKET_ORDER, payment.getPurpose());
        assertEquals(EVENT_ID, payment.getEventId());
        assertEquals(ORGANIZER_ID, payment.getOrganizerId());
        assertTrue(payment.refundedTotal().isZero());
        assertTrue(payment.getRefunds().isEmpty());
        assertNull(payment.getPaidAt());
    }

    @Test
    void zeroAmountCannotBeCharged() {
        assertThrows(InvalidPaymentStateException.class, () ->
            Payment.initiateForOrder(UUID.randomUUID(), EVENT_ID, ORGANIZER_ID, Money.zeroKzt())
        );
        assertThrows(InvalidPaymentStateException.class, () ->
            Payment.initiateActivationFee(EVENT_ID, ORGANIZER_ID, Money.zeroKzt())
        );
    }

    @Test
    void ticketOrderPaymentRequiresAnOrderId() {
        assertThrows(NullPointerException.class, () -> Payment.initiateForOrder(null, EVENT_ID, ORGANIZER_ID, Money.kzt(10000)));
    }

    @Test
    void ticketOrderPaymentCountsAsOrganizerRevenue() {
        Payment payment = pendingPayment(Money.kzt(10000));

        assertTrue(payment.countsAsOrganizerRevenue());
    }

    @Test
    void activationFeeHasNoOrderAndIsNotOrganizerRevenue() {
        Payment fee = Payment.initiateActivationFee(EVENT_ID, ORGANIZER_ID, Money.kzt(5000));

        assertEquals(PaymentPurpose.ACTIVATION_FEE, fee.getPurpose());
        assertNull(fee.getOrderId());
        assertFalse(fee.countsAsOrganizerRevenue());
    }

    // --- Success and failure ---

    @Test
    void successRecordsProviderReferenceAndTimestamp() {
        Payment payment = pendingPayment(Money.kzt(10000));

        payment.succeed("SIM-123", NOW);

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("SIM-123", payment.getProviderReference());
        assertEquals(NOW, payment.getPaidAt());
    }

    @Test
    void failureRecordsReasonAndTimestamp() {
        Payment payment = pendingPayment(Money.kzt(10000));

        payment.fail("card declined", NOW);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("card declined", payment.getFailureReason());
        assertEquals(NOW, payment.getFailedAt());
    }

    @Test
    void aPaymentCanOnlyLeavePendingOnce() {
        Payment payment = pendingPayment(Money.kzt(10000));

        payment.succeed("SIM-123", NOW);

        assertThrows(InvalidPaymentStateException.class, () -> payment.succeed("SIM-456", NOW.plusSeconds(1)));
        assertThrows(InvalidPaymentStateException.class, () -> payment.fail("declined", NOW.plusSeconds(1)));
    }

    // --- Refund preconditions ---

    @Test
    void pendingPaymentCannotBeRefunded() {
        Payment payment = pendingPayment(Money.kzt(10000));

        assertThrows(InvalidPaymentStateException.class, () -> payment.requestRefund(Money.kzt(1000), "too early", NOW));
    }

    @Test
    void failedPaymentCannotBeRefunded() {
        Payment payment = pendingPayment(Money.kzt(10000));

        payment.fail("card declined", NOW);

        assertThrows(InvalidPaymentStateException.class, () -> payment.requestRefund(Money.kzt(1000), "nothing to return", NOW));
    }

    @Test
    void zeroRefundIsRejected() {
        Payment payment = succeededPayment(Money.kzt(10000));

        assertThrows(InvalidPaymentStateException.class, () -> payment.requestRefund(Money.zeroKzt(), "nothing", NOW));
    }

    // --- Asynchronous refund lifecycle ---

    @Test
    void requestedRefundDoesNotChangeStatusUntilCompleted() {
        Payment payment = succeededPayment(Money.kzt(10000));

        Refund refund = payment.requestRefund(Money.kzt(4000), "one ticket returned", NOW);

        assertEquals(RefundStatus.REQUESTED, refund.getStatus());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertTrue(payment.refundedTotal().isZero());
        assertEquals(Money.kzt(4000), payment.reservedForRefunds());
        assertEquals(Money.kzt(6000), payment.refundableRemaining());
    }

    @Test
    void partialRefundLeavesTheRemainderRefundable() {
        Payment payment = succeededPayment(Money.kzt(10000));

        refundFully(payment, Money.kzt(4000), "one ticket returned", "SIM-R1", NOW);

        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
        assertEquals(Money.kzt(4000), payment.refundedTotal());
        assertTrue(payment.reservedForRefunds().isZero());
        assertEquals(Money.kzt(6000), payment.refundableRemaining());
        assertEquals(1, payment.getRefunds().size());
    }

    @Test
    void completedRefundRecordsProviderReferenceAndTimestamp() {
        Payment payment = succeededPayment(Money.kzt(10000));
        Refund refund = payment.requestRefund(Money.kzt(4000), "one ticket returned", NOW);

        payment.completeRefund(refund.getId(), "SIM-R1", NOW.plusSeconds(30));

        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertEquals("SIM-R1", refund.getProviderReference());
        assertEquals(NOW, refund.getRequestedAt());
        assertEquals(NOW.plusSeconds(30), refund.getCompletedAt());
    }

    @Test
    void refundsSummingToTheChargedAmountFullyRefundThePayment() {
        Payment payment = succeededPayment(Money.kzt(10000));

        refundFully(payment, Money.kzt(4000), "first ticket", "SIM-R1", NOW);
        refundFully(payment, Money.kzt(6000), "remaining tickets", "SIM-R2", NOW.plusSeconds(60));

        assertEquals(PaymentStatus.FULLY_REFUNDED, payment.getStatus());
        assertEquals(Money.kzt(10000), payment.refundedTotal());
        assertTrue(payment.refundableRemaining().isZero());
    }

    @Test
    void refundsCannotExceedTheChargedAmount() {
        Payment payment = succeededPayment(Money.kzt(10000));

        refundFully(payment, Money.kzt(7000), "most of it", "SIM-R1", NOW);

        assertThrows(RefundExceedsPaymentException.class, () ->
            payment.requestRefund(Money.kzt(4000), "too much", NOW.plusSeconds(60))
        );

        assertEquals(Money.kzt(7000), payment.refundedTotal());
        assertEquals(1, payment.getRefunds().size());
    }

    @Test
    void inFlightRefundsCountAgainstTheRemainder() {
        Payment payment = succeededPayment(Money.kzt(10000));

        payment.requestRefund(Money.kzt(7000), "first request", NOW);

        assertThrows(RefundExceedsPaymentException.class, () ->
            payment.requestRefund(Money.kzt(4000), "second request", NOW.plusSeconds(1))
        );
        assertEquals(1, payment.getRefunds().size());
    }

    @Test
    void failedRefundFreesItsReservedAmount() {
        Payment payment = succeededPayment(Money.kzt(10000));
        Refund refund = payment.requestRefund(Money.kzt(7000), "first request", NOW);

        payment.failRefund(refund.getId(), "provider rejected", NOW.plusSeconds(30));

        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertEquals("provider rejected", refund.getFailureReason());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertTrue(payment.refundedTotal().isZero());
        assertTrue(payment.reservedForRefunds().isZero());
        assertEquals(Money.kzt(10000), payment.refundableRemaining());
    }

    @Test
    void aRefundCanOnlyFinishOnce() {
        Payment payment = succeededPayment(Money.kzt(10000));
        Refund refund = payment.requestRefund(Money.kzt(4000), "one ticket", NOW);

        payment.completeRefund(refund.getId(), "SIM-R1", NOW.plusSeconds(30));

        assertThrows(InvalidPaymentStateException.class, () -> payment.completeRefund(refund.getId(), "SIM-R1", NOW.plusSeconds(60)));
        assertThrows(InvalidPaymentStateException.class, () -> payment.failRefund(refund.getId(), "late failure", NOW.plusSeconds(60)));
        assertEquals(Money.kzt(4000), payment.refundedTotal());
    }

    @Test
    void anUnknownRefundIdIsRejected() {
        Payment payment = succeededPayment(Money.kzt(10000));

        assertThrows(InvalidPaymentStateException.class, () -> payment.completeRefund(RefundId.generate(), "SIM-X", NOW));
    }

    @Test
    void aFullyRefundedPaymentAcceptsNoFurtherRefunds() {
        Payment payment = succeededPayment(Money.kzt(10000));

        refundFully(payment, Money.kzt(10000), "cancelled order", "SIM-R1", NOW);

        assertThrows(InvalidPaymentStateException.class, () -> payment.requestRefund(Money.kzt(1), "extra", NOW.plusSeconds(60)));
    }

    @Test
    void aFullyRefundedPaymentStillCountsAsSettled() {
        Payment payment = succeededPayment(Money.kzt(10000));

        refundFully(payment, Money.kzt(10000), "cancelled order", "SIM-R1", NOW);

        assertTrue(payment.getStatus().isSettled());
        assertFalse(payment.getStatus().allowsRefund());
    }

    @Test
    void refundsCannotBeAddedAroundTheAggregateRoot() {
        Payment payment = succeededPayment(Money.kzt(10000));

        assertThrows(UnsupportedOperationException.class, () ->
            payment.getRefunds().add(Refund.request(Money.kzt(99999), "bypass", NOW))
        );
    }

    // --- Helpers ---

    private static Payment pendingPayment(Money amount) {
        return Payment.initiateForOrder(UUID.randomUUID(), EVENT_ID, ORGANIZER_ID, amount);
    }

    private static Payment succeededPayment(Money amount) {
        Payment payment = pendingPayment(amount);
        payment.succeed("SIM-INITIAL", NOW);
        return payment;
    }

    private static void refundFully(Payment payment, Money amount, String reason, String providerReference, Instant at) {
        Refund refund = payment.requestRefund(amount, reason, at);
        payment.completeRefund(refund.getId(), providerReference, at);
    }
}
