package com.biletflow.biletflow.ticketinventory.application.ticket.command;

import java.util.Objects;
import java.util.UUID;

public record IssueTicketItem(UUID ticketTypeId, String attendeeEmail, Long ownerUserId, UUID seatId) {
    public IssueTicketItem {
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
        Objects.requireNonNull(attendeeEmail, "attendeeEmail cannot be null");

        if (attendeeEmail.isBlank()) {
            throw new IllegalArgumentException("attendeeEmail cannot be blank");
        }
    }
}
