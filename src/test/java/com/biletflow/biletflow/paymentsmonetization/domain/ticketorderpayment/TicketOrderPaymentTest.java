package com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.InvalidPaymentStateException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketOrderPaymentTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Long ORGANIZER_ID = 7L;

    @Test
    void initiatedPaymentStartsPendingWithoutRefund() {
        UUID orderId = UUID.randomUUID();

        TicketOrderPayment payment = TicketOrderPayment.initiate(orderId, UUID.randomUUID(), ORGANIZER_ID, Money.kzt(10000));

        assertEquals(TicketOrderPaymentStatus.PENDING, payment.getStatus());
        assertEquals(orderId, payment.getOrderId());
        assertTrue(payment.getRefund().isEmpty());
        assertFalse(payment.hasBeenCharged());
    }

    @Test
    void zeroAmountCannotBeCharged() {
        assertThrows(InvalidPaymentStateException.class, () ->
            TicketOrderPayment.initiate(UUID.randomUUID(), UUID.randomUUID(), ORGANIZER_ID, Money.zeroKzt())
        );
    }

    @Test
    void aTicketOrderPaymentRequiresAnOrderId() {
        assertThrows(NullPointerException.class, () ->
            TicketOrderPayment.initiate(null, UUID.randomUUID(), ORGANIZER_ID, Money.kzt(10000))
        );
    }

    @Test
    void successRecordsProviderReferenceAndTimestamp() {
        TicketOrderPayment payment = pendingPayment();

        payment.succeed("SIM-123", NOW);

        assertEquals(TicketOrderPaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("SIM-123", payment.getProviderReference());
        assertEquals(NOW, payment.getPaidAt());
        assertTrue(payment.hasBeenCharged());
    }

    @Test
    void failureRecordsReasonAndTimestamp() {
        TicketOrderPayment payment = pendingPayment();

        payment.fail("card declined", NOW);

        assertEquals(TicketOrderPaymentStatus.FAILED, payment.getStatus());
        assertEquals("card declined", payment.getFailureReason());
        assertEquals(NOW, payment.getFailedAt());
        assertFalse(payment.hasBeenCharged());
    }

    @Test
    void aPaymentCanOnlyLeavePendingOnce() {
        TicketOrderPayment payment = succeededPayment();

        assertThrows(InvalidPaymentStateException.class, () -> payment.succeed("SIM-456", NOW.plusSeconds(1)));
        assertThrows(InvalidPaymentStateException.class, () -> payment.fail("declined", NOW.plusSeconds(1)));
    }

    @Test
    void onlyASucceededPaymentReceivesRefunds() {
        TicketOrderPayment pending = pendingPayment();
        assertFalse(pending.receivesRefunds());
        assertThrows(InvalidPaymentStateException.class, () -> pending.requestRefund("too early", NOW));

        TicketOrderPayment failed = pendingPayment();
        failed.fail("card declined", NOW);
        assertFalse(failed.receivesRefunds());
        assertThrows(InvalidPaymentStateException.class, () -> failed.requestRefund("nothing to return", NOW));

        assertTrue(succeededPayment().receivesRefunds());
    }

    @Test
    void aRequestedRefundDoesNotChangeStatusUntilCompleted() {
        TicketOrderPayment payment = succeededPayment();

        Refund refund = payment.requestRefund("order cancelled", NOW);

        assertEquals(RefundStatus.REQUESTED, refund.getStatus());
        assertEquals(TicketOrderPaymentStatus.SUCCEEDED, payment.getStatus());
        assertFalse(payment.receivesRefunds());
    }

    @Test
    void aSecondRefundCannotBeRequestedWhileOneIsInFlight() {
        TicketOrderPayment payment = succeededPayment();
        payment.requestRefund("order cancelled", NOW);

        assertThrows(InvalidPaymentStateException.class, () -> payment.requestRefund("again", NOW.plusSeconds(1)));
    }

    @Test
    void completingTheRefundRefundsThePayment() {
        TicketOrderPayment payment = succeededPayment();
        payment.requestRefund("order cancelled", NOW);

        payment.completeRefund("SIM-R1", NOW.plusSeconds(60));

        assertEquals(TicketOrderPaymentStatus.REFUNDED, payment.getStatus());
        Refund refund = payment.getRefund().orElseThrow();
        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertEquals("SIM-R1", refund.getProviderReference());
        assertEquals(NOW.plusSeconds(60), refund.getCompletedAt());
    }

    @Test
    void aRefundedPaymentReceivesNoFurtherRefundsButStillCountsAsCharged() {
        TicketOrderPayment payment = refundedPayment();

        assertFalse(payment.receivesRefunds());
        assertThrows(InvalidPaymentStateException.class, () -> payment.requestRefund("extra", NOW.plusSeconds(120)));
        assertTrue(payment.hasBeenCharged());
    }

    @Test
    void completingARefundWithoutAProviderReferenceLeavesItUnchangedAndRetryable() {
        TicketOrderPayment payment = succeededPayment();
        Refund refund = payment.requestRefund("order cancelled", NOW);

        assertThrows(NullPointerException.class, () -> payment.completeRefund(null, NOW.plusSeconds(60)));

        assertRefundStillRequested(payment, refund);
        payment.completeRefund("SIM-R1", NOW.plusSeconds(60));
        assertEquals(TicketOrderPaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertEquals("SIM-R1", refund.getProviderReference());
        assertEquals(NOW.plusSeconds(60), refund.getCompletedAt());
    }

    @Test
    void completingARefundWithoutATimestampLeavesItUnchangedAndRetryable() {
        TicketOrderPayment payment = succeededPayment();
        Refund refund = payment.requestRefund("order cancelled", NOW);

        assertThrows(NullPointerException.class, () -> payment.completeRefund("SIM-INVALID", null));

        assertRefundStillRequested(payment, refund);
        payment.completeRefund("SIM-R1", NOW.plusSeconds(60));
        assertEquals(TicketOrderPaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertEquals("SIM-R1", refund.getProviderReference());
        assertEquals(NOW.plusSeconds(60), refund.getCompletedAt());
    }

    @Test
    void failingARefundWithoutATimestampLeavesItUnchangedAndRetryable() {
        TicketOrderPayment payment = succeededPayment();
        Refund refund = payment.requestRefund("order cancelled", NOW);

        assertThrows(NullPointerException.class, () -> payment.failRefund("invalid failure", null));

        assertRefundStillRequested(payment, refund);
        payment.failRefund("provider rejected", NOW.plusSeconds(60));
        assertEquals(TicketOrderPaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertEquals("provider rejected", refund.getFailureReason());
        assertEquals(NOW.plusSeconds(60), refund.getCompletedAt());
        assertTrue(payment.receivesRefunds());
    }

    private static void assertRefundStillRequested(TicketOrderPayment payment, Refund refund) {
        assertEquals(TicketOrderPaymentStatus.SUCCEEDED, payment.getStatus());
        assertSame(refund, payment.getRefund().orElseThrow());
        assertEquals(RefundStatus.REQUESTED, refund.getStatus());
        assertNull(refund.getProviderReference());
        assertNull(refund.getFailureReason());
        assertNull(refund.getCompletedAt());
        assertFalse(payment.receivesRefunds());
    }

    @Test
    void aFailedRefundCanBeRequestedAgain() {
        TicketOrderPayment payment = succeededPayment();
        Refund first = payment.requestRefund("order cancelled", NOW);

        payment.failRefund("provider timeout", NOW.plusSeconds(60));

        assertEquals(TicketOrderPaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(RefundStatus.FAILED, first.getStatus());
        assertTrue(payment.receivesRefunds());

        Refund second = payment.requestRefund("order cancelled", NOW.plusSeconds(120));
        assertNotEquals(first.getId(), second.getId());
        assertEquals(second, payment.getRefund().orElseThrow());
    }

    @Test
    void aRefundCanOnlyFinishOnce() {
        TicketOrderPayment payment = refundedPayment();

        assertThrows(InvalidPaymentStateException.class, () -> payment.completeRefund("SIM-R2", NOW.plusSeconds(120)));
        assertThrows(InvalidPaymentStateException.class, () -> payment.failRefund("late", NOW.plusSeconds(120)));
    }

    @Test
    void finishingARefundThatWasNeverRequestedIsRejected() {
        TicketOrderPayment payment = succeededPayment();

        assertThrows(InvalidPaymentStateException.class, () -> payment.completeRefund("SIM-R1", NOW));
        assertThrows(InvalidPaymentStateException.class, () -> payment.failRefund("nothing", NOW));
    }

    private static TicketOrderPayment pendingPayment() {
        return TicketOrderPayment.initiate(UUID.randomUUID(), UUID.randomUUID(), ORGANIZER_ID, Money.kzt(10000));
    }

    private static TicketOrderPayment succeededPayment() {
        TicketOrderPayment payment = pendingPayment();
        payment.succeed("SIM-INITIAL", NOW);
        return payment;
    }

    private static TicketOrderPayment refundedPayment() {
        TicketOrderPayment payment = succeededPayment();
        payment.requestRefund("order cancelled", NOW);
        payment.completeRefund("SIM-R1", NOW.plusSeconds(60));
        return payment;
    }
}
