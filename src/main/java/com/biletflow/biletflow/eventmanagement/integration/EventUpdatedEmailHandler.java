package com.biletflow.biletflow.eventmanagement.integration;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import com.biletflow.biletflow.common.events.IntegrationEventHandler;
import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.eventmanagement.application.notification.EventUpdateRecipientPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EventUpdatedEmailHandler implements IntegrationEventHandler {

    public static final String TYPE = "EVENT_UPDATED";
    private final EventUpdateRecipientPort recipients;
    private final EmailSender emailSender;

    public EventUpdatedEmailHandler(EventUpdateRecipientPort recipients, EmailSender emailSender) {
        this.recipients = Objects.requireNonNull(recipients);
        this.emailSender = Objects.requireNonNull(emailSender);
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public void handle(IntegrationEvent event) {
        UUID eventId = event.aggregateId();
        String title = event.attributes().getOrDefault("title", "An event you registered for");
        String changedFields = event.attributes().getOrDefault("changedFields", "Event details");
        for (String recipient : recipients.findRecipients(eventId)) {
            String stableKey = event.id() + ":" + recipient.toLowerCase(java.util.Locale.ROOT);
            UUID emailId = UUID.nameUUIDFromBytes(stableKey.getBytes(StandardCharsets.UTF_8));
            emailSender.send(new EmailSender.Email(
                emailId,
                recipient,
                "Event updated: " + title,
                "The organizer has updated the event '" + title + "'. Changes: " + changedFields
                    + ". Sign in to BiletFlow to review the latest event details."
            ));
        }
    }
}
