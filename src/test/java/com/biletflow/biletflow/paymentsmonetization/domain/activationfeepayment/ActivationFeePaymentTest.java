package com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.InvalidPaymentStateException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivationFeePaymentTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Long ORGANIZER_ID = 7L;

    @Test
    void initiatedPaymentStartsPending() {
        UUID eventId = UUID.randomUUID();

        ActivationFeePayment payment = ActivationFeePayment.initiate(eventId, ORGANIZER_ID, Money.kzt(5000));

        assertEquals(ActivationFeePaymentStatus.PENDING, payment.getStatus());
        assertEquals(eventId, payment.getEventId());
        assertFalse(payment.hasSucceeded());
    }

    @Test
    void zeroAmountCannotBeCharged() {
        assertThrows(InvalidPaymentStateException.class, () ->
            ActivationFeePayment.initiate(UUID.randomUUID(), ORGANIZER_ID, Money.zeroKzt())
        );
    }

    @Test
    void successRecordsProviderReferenceAndTimestamp() {
        ActivationFeePayment payment = pendingPayment();

        payment.succeed("SIM-FEE-1", NOW);

        assertTrue(payment.hasSucceeded());
        assertEquals("SIM-FEE-1", payment.getProviderReference());
        assertEquals(NOW, payment.getPaidAt());
    }

    @Test
    void failureRecordsReasonAndTimestamp() {
        ActivationFeePayment payment = pendingPayment();

        payment.fail("card declined", NOW);

        assertEquals(ActivationFeePaymentStatus.FAILED, payment.getStatus());
        assertFalse(payment.hasSucceeded());
        assertEquals("card declined", payment.getFailureReason());
        assertEquals(NOW, payment.getFailedAt());
    }

    @Test
    void aPaymentCanOnlyLeavePendingOnce() {
        ActivationFeePayment payment = pendingPayment();
        payment.succeed("SIM-FEE-1", NOW);

        assertThrows(InvalidPaymentStateException.class, () -> payment.succeed("SIM-FEE-2", NOW.plusSeconds(1)));
        assertThrows(InvalidPaymentStateException.class, () -> payment.fail("declined", NOW.plusSeconds(1)));
    }

    private static ActivationFeePayment pendingPayment() {
        return ActivationFeePayment.initiate(UUID.randomUUID(), ORGANIZER_ID, Money.kzt(5000));
    }
}
