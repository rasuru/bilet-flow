package com.biletflow.biletflow.eventmanagement.ohs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.PrivateAttendeeAccess;
import com.biletflow.biletflow.eventmanagement.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EventManagementSupportAccessTest {
    @Test
    void supportCaseStatusChangeIsRecordedInEventOwnedAuditTimeline() {
        Instant now = Instant.parse("2026-11-22T10:00:00Z");
        var audit = mock(EventAuditService.class);
        var repository = mock(SocialEventRepository.class);
        var ohs = new DefaultEventManagementOhs(repository, mock(VenueLayoutRepository.class), audit, mock(PrivateAttendeeAccess.class));
        var eventId = java.util.UUID.randomUUID();
        var caseId = java.util.UUID.randomUUID();

        ohs.recordSupportCaseStatusChanged(eventId, caseId, 20L, "OPEN", "RESOLVED", now);

        verify(audit).recordAt(eq(eventId), eq(20L),
            eq(EventAuditRepository.EventAuditType.SUPPORT_CASE_STATUS_CHANGED),
            contains(caseId.toString()), eq(now));
    }

    @Test
    void organizerAndAssignedEventAdminCanHandleSupportButUnassignedUserCannot() {
        Instant now = Instant.parse("2026-11-22T10:00:00Z");
        SocialEvent event = SocialEvent.createDraft(10L, "Event", "Description", "Music", "",
            EventVisibility.PUBLIC, new EventDateRange(now.plusSeconds(3600), now.plusSeconds(7200)),
            new RegistrationWindow.AlwaysOpen());
        event.assignStaff(20L, StaffRole.EVENT_ADMIN, now);
        event.assignStaff(21L, StaffRole.EVENT_ADMIN, now);

        SocialEventRepository repository = mock(SocialEventRepository.class);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(repository.findByOrganizerId(10L)).thenReturn(List.of(event));
        when(repository.findByStaffUserId(20L)).thenReturn(List.of(event));
        var ohs = new DefaultEventManagementOhs(repository, mock(VenueLayoutRepository.class),
            mock(EventAuditService.class), mock(PrivateAttendeeAccess.class));

        assertTrue(ohs.canHandleAttendeeSupport(event.getId().value(), 10L));
        assertTrue(ohs.canHandleAttendeeSupport(event.getId().value(), 20L));
        assertFalse(ohs.canHandleAttendeeSupport(event.getId().value(), 99L));
        // Support access is intentionally narrower than general ticketing management.
        assertFalse(ohs.canManageTicketing(event.getId().value(), 20L));
        assertEquals(List.of(event.getId().value()), ohs.findSupportManagedEventIds(10L));
        assertEquals(List.of(event.getId().value()), ohs.findSupportManagedEventIds(20L));
        var routing = ohs.supportRouting(event.getId().value()).orElseThrow();
        assertEquals(10L, routing.organizerUserId());
        assertEquals(List.of(20L, 21L), routing.supportStaffUserIds());
    }
}
