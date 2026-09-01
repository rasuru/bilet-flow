package com.biletflow.biletflow.eventmanagement.application.audit;

import java.time.Instant;
import java.util.UUID;

public interface EventAuditRepository {
    void append(EventAuditRecord record);

    record EventAuditRecord(UUID id, UUID eventId, Instant timestamp, Long actorUserId, EventAuditType type, String description) {}

    enum EventAuditType {
        CREATED,
        UPDATED,
        PUBLISHED,
        UNPUBLISHED,
        CANCELLED,
        VENUE_ATTACHED,
        TICKET_TYPE_ADDED,
        TICKET_TYPE_HIDDEN,
        TICKET_TYPE_REVEALED,
        STAFF_ASSIGNED,
        STAFF_REMOVED,
    }
}
