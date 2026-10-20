package com.biletflow.biletflow.ticketinventory.ohs;

import java.util.List;
import java.util.UUID;

/** Published event-notification audience; no ticket internals cross the boundary. */
public interface EventCancellationRecipientsOhs {
    List<String> attendeeEmails(UUID eventId);
}
