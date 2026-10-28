package com.biletflow.biletflow.paymentsmonetization.domain.activation;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePayment;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.ActivationNotAllowedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventPaidSalesActivationTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Long ORGANIZER_ID = 7L;

    @Test
    void requestedActivationStartsPendingAndBlocksPaidSales() {
        UUID eventId = UUID.randomUUID();

        EventPaidSalesActivation activation = EventPaidSalesActivation.request(eventId, ORGANIZER_ID, NOW);

        assertEquals(EventPaidSalesActivationStatus.PENDING, activation.getStatus());
        assertEquals(eventId, activation.getEventId());
        assertEquals(ORGANIZER_ID, activation.getOrganizerId());
        assertEquals(NOW, activation.getCreatedAt());
        assertFalse(activation.permitsPaidSales());
        assertNull(activation.getFeePaymentId());
    }

    @Test
    void activationRequiresAFeePayment() {
        EventPaidSalesActivation activation = pendingActivation();

        assertThrows(NullPointerException.class, () -> activation.activate(null, true));
        assertEquals(EventPaidSalesActivationStatus.PENDING, activation.getStatus());
    }

    @Test
    void anUnpaidFeeDoesNotActivate() {
        EventPaidSalesActivation activation = pendingActivation();
        ActivationFeePayment pendingFee = ActivationFeePayment.initiate(activation.getEventId(), ORGANIZER_ID, Money.kzt(5000));

        assertThrows(ActivationNotAllowedException.class, () -> activation.activate(pendingFee, true));
        assertEquals(EventPaidSalesActivationStatus.PENDING, activation.getStatus());
    }

    @Test
    void aFeePaidForAnotherEventDoesNotActivate() {
        EventPaidSalesActivation activation = pendingActivation();
        ActivationFeePayment otherEventFee = paidFee(UUID.randomUUID());

        assertThrows(ActivationNotAllowedException.class, () -> activation.activate(otherEventFee, true));
        assertEquals(EventPaidSalesActivationStatus.PENDING, activation.getStatus());
    }

    @Test
    void anUnsatisfiedChecklistLeavesTheActivationRetryable() {
        EventPaidSalesActivation activation = pendingActivation();
        ActivationFeePayment fee = paidFee(activation.getEventId());

        assertThrows(ActivationNotAllowedException.class, () -> activation.activate(fee, false));

        activation.activate(fee, true);
        assertEquals(EventPaidSalesActivationStatus.ACTIVE, activation.getStatus());
    }

    @Test
    void aFeePaidForAnotherOrganizerDoesNotActivateAndLeavesTheActivationRetryable() {
        EventPaidSalesActivation activation = pendingActivation();
        ActivationFeePayment otherOrganizerFee = ActivationFeePayment.initiate(activation.getEventId(), ORGANIZER_ID + 1, Money.kzt(5000));
        otherOrganizerFee.succeed("SIM-OTHER-ORGANIZER", NOW);

        assertThrows(ActivationNotAllowedException.class, () -> activation.activate(otherOrganizerFee, true));
        assertEquals(EventPaidSalesActivationStatus.PENDING, activation.getStatus());
        assertFalse(activation.permitsPaidSales());
        assertNull(activation.getFeePaymentId());

        ActivationFeePayment correctFee = paidFee(activation.getEventId());
        activation.activate(correctFee, true);

        assertTrue(activation.permitsPaidSales());
        assertEquals(correctFee.getId(), activation.getFeePaymentId());
    }

    @Test
    void activationPermitsPaidSales() {
        EventPaidSalesActivation activation = pendingActivation();
        ActivationFeePayment fee = paidFee(activation.getEventId());

        activation.activate(fee, true);

        assertEquals(EventPaidSalesActivationStatus.ACTIVE, activation.getStatus());
        assertTrue(activation.permitsPaidSales());
        assertEquals(fee.getId(), activation.getFeePaymentId());
    }

    @Test
    void anAlreadyActiveEventCannotBeActivatedAgain() {
        EventPaidSalesActivation activation = activeActivation();

        assertThrows(ActivationNotAllowedException.class, () -> activation.activate(paidFee(activation.getEventId()), true));
    }

    @Test
    void suspensionBlocksPaidSalesWithoutLosingTheFee() {
        EventPaidSalesActivation activation = activeActivation();
        var originalFee = activation.getFeePaymentId();

        activation.suspend("fraud investigation", NOW.plusSeconds(60));

        assertEquals(EventPaidSalesActivationStatus.SUSPENDED, activation.getStatus());
        assertFalse(activation.permitsPaidSales());
        assertEquals(originalFee, activation.getFeePaymentId());
        assertEquals("fraud investigation", activation.getSuspensionReason());
        assertEquals(NOW.plusSeconds(60), activation.getSuspendedAt());
    }

    @Test
    void onlyActivePaidSalesCanBeSuspended() {
        EventPaidSalesActivation pending = pendingActivation();
        assertThrows(ActivationNotAllowedException.class, () -> pending.suspend("too early", NOW));

        EventPaidSalesActivation suspended = activeActivation();
        suspended.suspend("first suspension", NOW);
        assertThrows(ActivationNotAllowedException.class, () -> suspended.suspend("again", NOW.plusSeconds(60)));
    }

    @Test
    void reinstatementRestoresPaidSalesWithoutANewFee() {
        EventPaidSalesActivation activation = activeActivation();
        var originalFee = activation.getFeePaymentId();
        activation.suspend("fraud investigation", NOW.plusSeconds(60));

        activation.reinstate();

        assertEquals(EventPaidSalesActivationStatus.ACTIVE, activation.getStatus());
        assertTrue(activation.permitsPaidSales());
        assertEquals(originalFee, activation.getFeePaymentId());
        assertNull(activation.getSuspendedAt());
        assertNull(activation.getSuspensionReason());
    }

    @Test
    void onlySuspendedPaidSalesCanBeReinstated() {
        EventPaidSalesActivation active = activeActivation();
        assertThrows(ActivationNotAllowedException.class, active::reinstate);

        EventPaidSalesActivation pending = pendingActivation();
        assertThrows(ActivationNotAllowedException.class, pending::reinstate);
    }

    @Test
    void anAdministrativeSuspensionCannotBeBypassedWithANewFeeAttempt() {
        EventPaidSalesActivation activation = pendingActivation();
        ActivationFeePayment fee = paidFee(activation.getEventId());
        activation.activate(fee, true);
        activation.suspend("fraud investigation", NOW.plusSeconds(60));

        assertThrows(ActivationNotAllowedException.class, () -> activation.requireNewFeeAttemptAllowed(fee));
    }

    @Test
    void aRefundedFeeAllowsANewFeeAttempt() {
        EventPaidSalesActivation activation = pendingActivation();
        ActivationFeePayment fee = paidFee(activation.getEventId());
        activation.activate(fee, true);
        activation.suspend("Activation fee refunded", NOW.plusSeconds(60));
        fee.requestCompensatingRefund();
        fee.completeCompensatingRefund();

        assertDoesNotThrow(() -> activation.requireNewFeeAttemptAllowed(fee));
        assertDoesNotThrow(() -> pendingActivation().requireNewFeeAttemptAllowed(paidFee(UUID.randomUUID())));
    }

    private static EventPaidSalesActivation pendingActivation() {
        return EventPaidSalesActivation.request(UUID.randomUUID(), ORGANIZER_ID, NOW);
    }

    private static EventPaidSalesActivation activeActivation() {
        EventPaidSalesActivation activation = pendingActivation();
        activation.activate(paidFee(activation.getEventId()), true);
        return activation;
    }

    private static ActivationFeePayment paidFee(UUID eventId) {
        ActivationFeePayment fee = ActivationFeePayment.initiate(eventId, ORGANIZER_ID, Money.kzt(5000));
        fee.succeed("SIM-FEE", NOW);
        return fee;
    }
}
