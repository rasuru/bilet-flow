package com.biletflow.biletflow.ordercheckout.application.refundpolicy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.persistence.refundpolicy.RefundPolicyJpaEntity;
import com.biletflow.biletflow.ordercheckout.persistence.refundpolicy.RefundPolicyJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundPolicyServiceTest {
    @Test
    void organizerCanDefineBasicRefundRulesAndAttendeeReadableTerms() {
        UUID eventId = UUID.randomUUID();
        CurrentActor actor = () -> Optional.of(77L);
        RefundPolicyJpaRepository policies = mock(RefundPolicyJpaRepository.class);
        EventManagementOhs events = mock(EventManagementOhs.class);
        when(events.canManageTicketing(eventId, 77L)).thenReturn(true);
        when(policies.findById(eventId)).thenReturn(Optional.empty());
        when(policies.save(any())).thenAnswer(call -> call.getArgument(0));
        RefundPolicyService service = new RefundPolicyService(policies, events, actor,
            Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC));

        RefundPolicyView updated = service.update(eventId, true, 24, "Refunds are available until 24 hours before the event.");
        assertTrue(updated.refundsAllowed());
        assertEquals(24, updated.cutoffHoursBeforeEvent());
        assertEquals("Refunds are available until 24 hours before the event.", updated.terms());
        verify(policies).save(argThat(policy -> "Refunds are available until 24 hours before the event.".equals(policy.getTerms())));
    }

    @Test
    void rejectsTermsLongerThanTheSupportedLimit() {
        UUID eventId = UUID.randomUUID();
        CurrentActor actor = () -> Optional.of(77L);
        RefundPolicyJpaRepository policies = mock(RefundPolicyJpaRepository.class);
        EventManagementOhs events = mock(EventManagementOhs.class);
        when(events.canManageTicketing(eventId, 77L)).thenReturn(true);
        when(policies.findById(eventId)).thenReturn(Optional.empty());
        when(policies.save(any())).thenAnswer(call -> call.getArgument(0));
        RefundPolicyService service = new RefundPolicyService(policies, events, actor, Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> service.update(eventId, true, 24, "x".repeat(2001)));
    }
}
