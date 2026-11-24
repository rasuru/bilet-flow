package com.biletflow.biletflow.eventmanagement.application.notification;

import java.util.List;
import java.util.UUID;

/** Event Management's outbound port for attendee email addresses that should receive event updates. */
public interface EventUpdateRecipientPort {
    List<String> findRecipients(UUID eventId);
}
