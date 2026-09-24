package com.biletflow.biletflow.payments.domain.activation;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.payments.domain.exceptions.ActivationNotAllowedException;
import com.biletflow.biletflow.payments.domain.payment.PaymentId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivationRecordTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Long ORGANIZER_ID = 7L;

    @Test
    void requestedActivationStartsPendingAndBlocksPaidSales() {
        ActivationRecord record = ActivationRecord.request(UUID.randomUUID(), ORGANIZER_ID);

        assertEquals(ActivationStatus.PENDING, record.getStatus());
        assertFalse(record.permitsPaidSales());
        assertFalse(record.isFeeSettled());
        assertNull(record.getActivatedAt());
    }

    @Test
    void activationRequiresASettledFeePayment() {
        ActivationRecord record = ActivationRecord.request(UUID.randomUUID(), ORGANIZER_ID);

        assertThrows(NullPointerException.class, () -> record.activate(null, true, NOW));
        assertEquals(ActivationStatus.PENDING, record.getStatus());
    }

    @Test
    void activationRequiresAnEligibleOrganizer() {
        ActivationRecord record = ActivationRecord.request(UUID.randomUUID(), ORGANIZER_ID);

        assertThrows(ActivationNotAllowedException.class, () -> record.activate(PaymentId.generate(), false, NOW));
        assertEquals(ActivationStatus.PENDING, record.getStatus());
    }

    @Test
    void aFailedFeePaymentLeavesTheRecordRetryable() {
        ActivationRecord record = ActivationRecord.request(UUID.randomUUID(), ORGANIZER_ID);

        assertThrows(ActivationNotAllowedException.class, () -> record.activate(PaymentId.generate(), false, NOW));

        PaymentId fee = PaymentId.generate();
        record.activate(fee, true, NOW.plusSeconds(60));

        assertEquals(ActivationStatus.ACTIVE, record.getStatus());
        assertEquals(fee, record.getFeePaymentId());
    }

    @Test
    void activationPermitsPaidSales() {
        PaymentId fee = PaymentId.generate();
        ActivationRecord record = ActivationRecord.request(UUID.randomUUID(), ORGANIZER_ID);

        record.activate(fee, true, NOW);

        assertEquals(ActivationStatus.ACTIVE, record.getStatus());
        assertTrue(record.permitsPaidSales());
        assertTrue(record.isFeeSettled());
        assertEquals(NOW, record.getActivatedAt());
    }

    @Test
    void anAlreadyActiveEventCannotBeActivatedAgain() {
        ActivationRecord record = activeRecord();

        assertThrows(ActivationNotAllowedException.class, () -> record.activate(PaymentId.generate(), true, NOW.plusSeconds(60)));
    }

    @Test
    void suspensionBlocksPaidSalesWithoutLosingTheFee() {
        ActivationRecord record = activeRecord();
        PaymentId originalFee = record.getFeePaymentId();

        record.suspend("fraud investigation", NOW.plusSeconds(60));

        assertEquals(ActivationStatus.SUSPENDED, record.getStatus());
        assertFalse(record.permitsPaidSales());
        assertTrue(record.isFeeSettled());
        assertEquals(originalFee, record.getFeePaymentId());
        assertEquals("fraud investigation", record.getSuspensionReason());
    }

    @Test
    void onlyActivePaidSalesCanBeSuspended() {
        ActivationRecord pending = ActivationRecord.request(UUID.randomUUID(), ORGANIZER_ID);
        assertThrows(ActivationNotAllowedException.class, () -> pending.suspend("too early", NOW));

        ActivationRecord suspended = activeRecord();
        suspended.suspend("first suspension", NOW);
        assertThrows(ActivationNotAllowedException.class, () -> suspended.suspend("again", NOW.plusSeconds(60)));
    }

    @Test
    void reinstatementRestoresPaidSalesWithoutANewFee() {
        ActivationRecord record = activeRecord();
        PaymentId originalFee = record.getFeePaymentId();

        record.suspend("fraud investigation", NOW.plusSeconds(60));
        record.reinstate(NOW.plusSeconds(120));

        assertEquals(ActivationStatus.ACTIVE, record.getStatus());
        assertTrue(record.permitsPaidSales());
        assertEquals(originalFee, record.getFeePaymentId());
        assertNull(record.getSuspendedAt());
        assertNull(record.getSuspensionReason());
    }

    @Test
    void onlySuspendedPaidSalesCanBeReinstated() {
        ActivationRecord active = activeRecord();
        assertThrows(ActivationNotAllowedException.class, () -> active.reinstate(NOW.plusSeconds(60)));

        ActivationRecord pending = ActivationRecord.request(UUID.randomUUID(), ORGANIZER_ID);
        assertThrows(ActivationNotAllowedException.class, () -> pending.reinstate(NOW));
    }

    @Test
    void activationIsScopedToTheEventItWasRequestedFor() {
        UUID eventId = UUID.randomUUID();

        ActivationRecord record = ActivationRecord.request(eventId, ORGANIZER_ID);

        assertEquals(eventId, record.getEventId());
        assertEquals(ORGANIZER_ID, record.getOrganizerId());
    }

    private static ActivationRecord activeRecord() {
        ActivationRecord record = ActivationRecord.request(UUID.randomUUID(), ORGANIZER_ID);
        record.activate(PaymentId.generate(), true, NOW);
        return record;
    }
}
