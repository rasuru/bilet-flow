package com.biletflow.biletflow.ticketinventory.application.ticket.command;

import java.util.Objects;
import java.util.UUID;

public record LinkTicketOwnerCommand(UUID ticketId, Long userId) {
    public LinkTicketOwnerCommand {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
    }
}
