package com.biletflow.biletflow.payments.domain.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.payments.domain.exceptions.InvalidPaymentsStateException;
import com.biletflow.biletflow.payments.domain.exceptions.RefundExceedsPaymentsException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void initiatedPaymentStartsPendingWithNoRefunds() {
        Payment payment = Payment.initiate(UUID.randomUUID(), Money.kzt(10000));

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertTrue(payment.refundedTotal().isZero());
        assertTrue(payment.getRefunds().isEmpty());
        assertNull(payment.getPaidAt());
    }

    @Test
    void zeroAmountCannotBeCharged() {
        assertThrows(InvalidPaymentsStateException.class, () -> Payment.initiate(UUID.randomUUID(), Money.zeroKzt()));
    }

    @Test
    void successRecordsProviderReferenceAndTimestamp() {
        Payment payment = pendingPayment(Money.kzt(10000));

        payment.succeed("SIM-123", NOW);

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("SIM-123", payment.getProviderReference());
        assertEquals(NOW, payment.getPaidAt());
    }

    @Test
    void aPaymentCanOnlyLeavePendingOnce() {
        Payment payment = pendingPayment(Money.kzt(10000));

        payment.succeed("SIM-123", NOW);

        assertThrows(InvalidPaymentsStateException.class, () -> payment.succeed("SIM-456", NOW.plusSeconds(1)));
        assertThrows(InvalidPaymentsStateException.class, () -> payment.fail("declined", NOW.plusSeconds(1)));
    }

    @Test
    void pendingPaymentCannotBeRefunded() {
        Payment payment = pendingPayment(Money.kzt(10000));

        assertThrows(InvalidPaymentsStateException.class, () -> payment.refund(Money.kzt(1000), "too early", "SIM-R1", NOW));
    }

    @Test
    void failedPaymentCannotBeRefunded() {
        Payment payment = pendingPayment(Money.kzt(10000));

        payment.fail("card declined", NOW);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertThrows(InvalidPaymentsStateException.class, () -> payment.refund(Money.kzt(1000), "nothing to return", "SIM-R1", NOW));
    }

    @Test
    void partialRefundLeavesTheRemainderRefundable() {
        Payment payment = succeededPayment(Money.kzt(10000));

        payment.refund(Money.kzt(4000), "one ticket returned", "SIM-R1", NOW);

        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
        assertEquals(Money.kzt(4000), payment.refundedTotal());
        assertEquals(Money.kzt(6000), payment.refundableRemaining());
        assertEquals(1, payment.getRefunds().size());
    }

    @Test
    void refundsSummingToTheChargedAmountFullyRefundThePayment() {
        Payment payment = succeededPayment(Money.kzt(10000));

        payment.refund(Money.kzt(4000), "first ticket", "SIM-R1", NOW);
        payment.refund(Money.kzt(6000), "remaining tickets", "SIM-R2", NOW.plusSeconds(60));

        assertEquals(PaymentStatus.FULLY_REFUNDED, payment.getStatus());
        assertEquals(Money.kzt(10000), payment.refundedTotal());
        assertTrue(payment.refundableRemaining().isZero());
    }

    @Test
    void refundsCannotExceedTheChargedAmount() {
        Payment payment = succeededPayment(Money.kzt(10000));

        payment.refund(Money.kzt(7000), "most of it", "SIM-R1", NOW);

        assertThrows(RefundExceedsPaymentsException.class, () ->
            payment.refund(Money.kzt(4000), "too much", "SIM-R2", NOW.plusSeconds(60))
        );

        assertEquals(Money.kzt(7000), payment.refundedTotal());
        assertEquals(1, payment.getRefunds().size());
    }

    @Test
    void aFullyRefundedPaymentAcceptsNoFurtherRefunds() {
        Payment payment = succeededPayment(Money.kzt(10000));

        payment.refund(Money.kzt(10000), "cancelled order", "SIM-R1", NOW);

        assertThrows(InvalidPaymentsStateException.class, () -> payment.refund(Money.kzt(1), "extra", "SIM-R2", NOW.plusSeconds(60)));
    }

    @Test
    void zeroRefundIsRejected() {
        Payment payment = succeededPayment(Money.kzt(10000));

        assertThrows(InvalidPaymentsStateException.class, () -> payment.refund(Money.zeroKzt(), "nothing", "SIM-R1", NOW));
    }

    @Test
    void refundsCannotBeAddedAroundTheAggregateRoot() {
        Payment payment = succeededPayment(Money.kzt(10000));

        assertThrows(UnsupportedOperationException.class, () ->
            payment.getRefunds().add(new Refund(Money.kzt(99999), "bypass", NOW, "SIM-X"))
        );
    }

    @Test
    void aFullyRefundedPaymentStillCountsAsSettled() {
        Payment payment = succeededPayment(Money.kzt(10000));

        payment.refund(Money.kzt(10000), "cancelled order", "SIM-R1", NOW);

        assertTrue(payment.getStatus().isSettled());
        assertFalse(payment.getStatus().allowsRefund());
    }

    private static Payment pendingPayment(Money amount) {
        return Payment.initiate(UUID.randomUUID(), amount);
    }

    private static Payment succeededPayment(Money amount) {
        Payment payment = pendingPayment(amount);
        payment.succeed("SIM-INITIAL", NOW);
        return payment;
    }
}
