package com.biletflow.biletflow.ticketinventory.application.ticket.view;

import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import java.util.Objects;
import java.util.UUID;

public record TicketValidationResult(
    UUID ticketId,
    UUID eventId,
    UUID ticketTypeId,
    UUID seatId,
    TicketStatus status,
    boolean validForEntry
) {
    public TicketValidationResult {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }
}
