package com.biletflow.biletflow.ticketinventory.application.ticket.command;

import java.util.Objects;
import java.util.UUID;

public record CancelTicketCommand(UUID ticketId) {
    public CancelTicketCommand {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
    }
}
