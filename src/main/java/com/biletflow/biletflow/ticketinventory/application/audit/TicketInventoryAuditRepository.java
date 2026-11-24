package com.biletflow.biletflow.ticketinventory.application.audit;

import java.time.Instant;
import java.util.UUID;

public interface TicketInventoryAuditRepository {
    void append(TicketInventoryAuditRecord record);

    record TicketInventoryAuditRecord(
        UUID id,
        UUID eventId,
        Instant timestamp,
        Long actorUserId,
        TicketInventoryAuditType type,
        UUID entityId,
        String description
    ) {}

    enum TicketInventoryAuditType {
        TICKET_TYPE_CREATED,
        TICKET_PRICE_CHANGED,
        TICKET_CAPACITY_CHANGED,
        TICKET_CHECKED_IN,
        CHECK_IN_REVERSED,
    }
}
