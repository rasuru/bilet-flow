package com.biletflow.biletflow.eventmanagement.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.eventmanagement.domain.exceptions.InvalidEventStateException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefundPolicyAndPrivateAccessTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final EventDateRange DATES = new EventDateRange(NOW.plusSeconds(7 * 86_400), NOW.plusSeconds(7 * 86_400 + 7_200));

    private static SocialEvent event(EventVisibility visibility) {
        return SocialEvent.createDraft(10L, "Event", "Description", "Music", "", visibility, DATES, new RegistrationWindow.AlwaysOpen());
    }

    @Test
    void refundPolicyComputesWhenRefundsClose() {
        var policy = new RefundPolicy(true, 48, "  Full refund on request  ");
        assertEquals(DATES.startAt().minusSeconds(48 * 3_600), policy.refundableUntil(DATES).orElseThrow());
        assertEquals("Full refund on request", policy.terms());
        assertEquals("Refunds until 48 hours before the event", policy.summary());

        var untilStart = new RefundPolicy(true, 0, null);
        assertEquals(DATES.startAt(), untilStart.refundableUntil(DATES).orElseThrow());

        var none = new RefundPolicy(false, 0, "   ");
        assertTrue(none.refundableUntil(DATES).isEmpty());
        assertNull(none.terms());
        assertEquals("No refunds", none.summary());
    }

    @Test
    void invalidRefundPoliciesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RefundPolicy(true, -1, null));
        assertThrows(IllegalArgumentException.class, () -> new RefundPolicy(true, RefundPolicy.MAX_CUTOFF_HOURS + 1, null));
        assertThrows(IllegalArgumentException.class, () -> new RefundPolicy(false, 24, null));
        assertThrows(IllegalArgumentException.class, () -> new RefundPolicy(true, 0, "x".repeat(RefundPolicy.MAX_TERMS_LENGTH + 1)));
    }

    @Test
    void organizerCanDefineReplaceAndRemoveThePolicy() {
        var event = event(EventVisibility.PUBLIC);
        assertTrue(event.getRefundPolicy().isEmpty());

        event.defineRefundPolicy(new RefundPolicy(true, 24, null));
        event.defineRefundPolicy(new RefundPolicy(false, 0, "All sales final"));
        assertEquals(new RefundPolicy(false, 0, "All sales final"), event.getRefundPolicy().orElseThrow());

        assertTrue(event.removeRefundPolicy());
        assertFalse(event.removeRefundPolicy());
        assertTrue(event.getRefundPolicy().isEmpty());
    }

    @Test
    void cancelledEventPolicyCannotChange() {
        var event = event(EventVisibility.PUBLIC);
        event.cancel(NOW);
        assertThrows(InvalidEventStateException.class, () -> event.defineRefundPolicy(new RefundPolicy(true, 0, null)));
        assertThrows(InvalidEventStateException.class, event::removeRefundPolicy);
    }

    @Test
    void duplicateKeepsThePolicyButGetsItsOwnAccessCode() {
        var original = event(EventVisibility.PRIVATE);
        original.defineRefundPolicy(new RefundPolicy(true, 72, "Terms"));

        var copy = original.duplicate(DATES, new RegistrationWindow.AlwaysOpen());

        assertEquals(original.getRefundPolicy(), copy.getRefundPolicy());
        assertTrue(copy.getPrivateAccessCode().isPresent());
        assertNotEquals(original.getPrivateAccessCode(), copy.getPrivateAccessCode());
    }

    @Test
    void publicAndUnlistedEventsAreOpenToEveryone() {
        for (var visibility : new EventVisibility[] { EventVisibility.PUBLIC, EventVisibility.UNLISTED }) {
            var event = event(visibility);
            assertTrue(event.getPrivateAccessCode().isEmpty());
            assertTrue(event.grantsAttendeeAccess(null));
            assertTrue(event.grantsAttendeeAccess("anything"));
        }
    }

    @Test
    void privateEventNeedsItsCurrentAccessCode() {
        var event = event(EventVisibility.PRIVATE);
        String code = event.getPrivateAccessCode().orElseThrow().value();

        assertTrue(event.grantsAttendeeAccess(code));
        assertTrue(event.grantsAttendeeAccess("  " + code + " "));
        assertFalse(event.grantsAttendeeAccess(null));
        assertFalse(event.grantsAttendeeAccess(""));
        assertFalse(event.grantsAttendeeAccess(code.substring(1)));
        assertFalse(event.grantsAttendeeAccess(PrivateAccessCode.generate().value()));
    }

    @Test
    void rotatingTheCodeRevokesTheOldInviteLink() {
        var event = event(EventVisibility.PRIVATE);
        String old = event.getPrivateAccessCode().orElseThrow().value();

        String replacement = event.rotatePrivateAccessCode().value();

        assertNotEquals(old, replacement);
        assertFalse(event.grantsAttendeeAccess(old));
        assertTrue(event.grantsAttendeeAccess(replacement));
    }

    @Test
    void makingAnEventPrivateCreatesACode() {
        var event = event(EventVisibility.PUBLIC);
        event.updateDetails("Event", "Description", "Music", "", EventVisibility.PRIVATE, DATES, new RegistrationWindow.AlwaysOpen());

        String code = event.getPrivateAccessCode().orElseThrow().value();
        assertFalse(event.grantsAttendeeAccess(null));
        assertTrue(event.grantsAttendeeAccess(code));
    }

    @Test
    void onlyPrivateEventsCanRotateACode() {
        assertThrows(InvalidEventStateException.class, () -> event(EventVisibility.PUBLIC).rotatePrivateAccessCode());
        assertThrows(InvalidEventStateException.class, () -> event(EventVisibility.UNLISTED).rotatePrivateAccessCode());
    }

    @Test
    void generatedCodesAreUrlSafeAndUnique() {
        var first = PrivateAccessCode.generate().value();
        var second = PrivateAccessCode.generate().value();
        assertTrue(first.matches("[A-Za-z0-9_-]{32}"));
        assertNotEquals(first, second);
        assertThrows(IllegalArgumentException.class, () -> new PrivateAccessCode("short"));
        assertThrows(IllegalArgumentException.class, () -> new PrivateAccessCode("has spaces in it !!"));
    }
}
