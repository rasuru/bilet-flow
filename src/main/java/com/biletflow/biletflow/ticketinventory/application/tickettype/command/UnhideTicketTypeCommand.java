package com.biletflow.biletflow.ticketinventory.application.tickettype.command;

import java.util.Objects;
import java.util.UUID;

public record UnhideTicketTypeCommand(UUID ticketTypeId) {
    public UnhideTicketTypeCommand {
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
    }
}
