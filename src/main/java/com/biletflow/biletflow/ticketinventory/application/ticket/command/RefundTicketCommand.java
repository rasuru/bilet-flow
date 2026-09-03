package com.biletflow.biletflow.ticketinventory.application.ticket.command;

import java.util.Objects;
import java.util.UUID;

public record RefundTicketCommand(UUID ticketId) {
    public RefundTicketCommand {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
    }
}
