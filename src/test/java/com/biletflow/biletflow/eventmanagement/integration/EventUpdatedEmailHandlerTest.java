package com.biletflow.biletflow.eventmanagement.integration;

import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.eventmanagement.application.notification.EventUpdateRecipientPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventUpdatedEmailHandlerTest {

    @Test
    void sendsAnUpdateEmailToEachUniqueEligibleAttendeeAddress() {
        EventUpdateRecipientPort recipients = mock(EventUpdateRecipientPort.class);
        EmailSender sender = mock(EmailSender.class);
        UUID eventId = UUID.randomUUID();
        when(recipients.findRecipients(eventId)).thenReturn(List.of("one@example.com", "two@example.com"));

        new EventUpdatedEmailHandler(recipients, sender).handle(new IntegrationEvent(
            UUID.randomUUID(), EventUpdatedEmailHandler.TYPE, eventId, Map.of("title", "Updated event")
        ));

        verify(sender, times(2)).send(any(EmailSender.Email.class));
    }
}
