package com.biletflow.biletflow.eventmanagement.application.cancellation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.ohs.EventCancelled;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CancellationNoticesTest {

    @Test
    void notifiesCurrentAttendeesOrganizerAndStaffWithoutPromisingRefundsOrReissuingTickets() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        var event = SocialEvent.createDraft(
            7L,
            "Meetup",
            "Description",
            "Technology",
            "https://example.com/image.png",
            EventVisibility.UNLISTED,
            new EventDateRange(now.plusSeconds(3600), now.plusSeconds(7200)),
            new RegistrationWindow.AlwaysOpen()
        );
        event.assignStaff(8L, StaffRole.EVENT_ADMIN, now);
        event.cancel(now);
        var events = mock(SocialEventRepository.class);
        var recipients = mock(CancellationRecipients.class);
        var store = mock(CancellationNoticeStore.class);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(recipients.attendees(event.getId().value())).thenReturn(List.of("attendee@example.com", "organizer@example.com"));
        when(recipients.verifiedEmail(7L)).thenReturn(Optional.of("organizer@example.com"));
        when(recipients.verifiedEmail(8L)).thenReturn(Optional.of("staff@example.com"));
        var handler = new CancellationNotices(events, recipients, store);
        handler.handle(new EventCancelled(event.getId().value(), now).toEvent());
        var notices = ArgumentCaptor.forClass(CancellationNotice.class);
        verify(store, times(3)).enqueue(notices.capture());
        assertEquals(
            Set.of("attendee@example.com", "organizer@example.com", "staff@example.com"),
            new HashSet<>(notices.getAllValues().stream().map(CancellationNotice::recipient).toList())
        );
        assertEquals(
            notices.getAllValues().get(0).id(),
            CancellationNotice.forRecipient(
                event.getId().value(),
                " ATTENDEE@EXAMPLE.COM ",
                "Meetup",
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                now
            ).id()
        );
    }
}
