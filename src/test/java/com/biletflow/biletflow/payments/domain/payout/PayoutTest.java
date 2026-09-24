package com.biletflow.biletflow.payments.domain.payout;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.payments.domain.exceptions.InvalidPayoutStateException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PayoutTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Long ORGANIZER_ID = 7L;

    @Test
    void requestedPayoutStartsRequested() {
        Payout payout = Payout.request(ORGANIZER_ID, Money.kzt(8000), NOW);

        assertEquals(PayoutStatus.REQUESTED, payout.getStatus());
        assertEquals(ORGANIZER_ID, payout.getOrganizerId());
        assertEquals(Money.kzt(8000), payout.getAmount());
        assertEquals(NOW, payout.getRequestedAt());
        assertNull(payout.getCompletedAt());
    }

    @Test
    void zeroPayoutIsRejected() {
        assertThrows(InvalidPayoutStateException.class, () -> Payout.request(ORGANIZER_ID, Money.zeroKzt(), NOW));
    }

    @Test
    void completionRecordsProviderReferenceAndTimestamp() {
        Payout payout = Payout.request(ORGANIZER_ID, Money.kzt(8000), NOW);

        payout.complete("SIM-P1", NOW.plusSeconds(60));

        assertEquals(PayoutStatus.COMPLETED, payout.getStatus());
        assertEquals("SIM-P1", payout.getProviderReference());
        assertEquals(NOW.plusSeconds(60), payout.getCompletedAt());
    }

    @Test
    void failureRecordsReasonAndTimestamp() {
        Payout payout = Payout.request(ORGANIZER_ID, Money.kzt(8000), NOW);

        payout.fail("simulated bank rejection", NOW.plusSeconds(60));

        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        assertEquals("simulated bank rejection", payout.getFailureReason());
        assertEquals(NOW.plusSeconds(60), payout.getCompletedAt());
    }

    @Test
    void aPayoutCanOnlyFinishOnce() {
        Payout completed = Payout.request(ORGANIZER_ID, Money.kzt(8000), NOW);
        completed.complete("SIM-P1", NOW);
        assertThrows(InvalidPayoutStateException.class, () -> completed.complete("SIM-P2", NOW.plusSeconds(1)));
        assertThrows(InvalidPayoutStateException.class, () -> completed.fail("late", NOW.plusSeconds(1)));

        Payout failed = Payout.request(ORGANIZER_ID, Money.kzt(8000), NOW);
        failed.fail("rejected", NOW);
        assertThrows(InvalidPayoutStateException.class, () -> failed.complete("SIM-P3", NOW.plusSeconds(1)));
    }
}
