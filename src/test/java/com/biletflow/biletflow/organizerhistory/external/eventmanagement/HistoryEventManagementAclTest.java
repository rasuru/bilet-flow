package com.biletflow.biletflow.organizerhistory.external.eventmanagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.PrivateAttendeeAccess;
import com.biletflow.biletflow.eventmanagement.domain.EventDateRange;
import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.RegistrationWindow;
import com.biletflow.biletflow.eventmanagement.domain.SocialEvent;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventRepository;
import com.biletflow.biletflow.eventmanagement.domain.StaffRole;
import com.biletflow.biletflow.eventmanagement.domain.VenueLayoutRepository;
import com.biletflow.biletflow.eventmanagement.ohs.DefaultEventManagementOhs;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryEventManagementAclTest {

    private static final long ORGANIZER = 10L;
    private static final long EVENT_ADMIN = 20L;
    private static final long STRANGER = 99L;

    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");
    private final SocialEventRepository repository = mock(SocialEventRepository.class);
    private final HistoryEventManagementAcl acl = new HistoryEventManagementAcl(
        new DefaultEventManagementOhs(repository, mock(VenueLayoutRepository.class), mock(EventAuditService.class), mock(PrivateAttendeeAccess.class))
    );

    private SocialEvent event() {
        SocialEvent event = SocialEvent.createDraft(
            ORGANIZER,
            "Event",
            "Description",
            "Music",
            "",
            EventVisibility.PUBLIC,
            new EventDateRange(now.plusSeconds(3600), now.plusSeconds(7200)),
            new RegistrationWindow.AlwaysOpen()
        );
        event.assignStaff(EVENT_ADMIN, StaffRole.EVENT_ADMIN, now);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        return event;
    }

    @Test
    void onlyTheOrganizerMayReadHistory() {
        UUID eventId = event().getId().value();
        assertTrue(acl.canManage(eventId, ORGANIZER));
        assertFalse(acl.canManage(eventId, EVENT_ADMIN));
        assertFalse(acl.canManage(eventId, STRANGER));
    }

    @Test
    void unknownEventIsReportedAsNotFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> acl.canManage(UUID.randomUUID(), ORGANIZER));
    }
}
