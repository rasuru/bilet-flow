package com.biletflow.biletflow.ticketinventory.application.ticket.command;

import java.util.Objects;
import java.util.UUID;

public record ClaimTicketCommand(UUID ticketId) {
    public ClaimTicketCommand {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
    }
}
