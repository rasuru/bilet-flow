package com.biletflow.biletflow.ticketinventory.application.tickettype.command;

import java.util.Objects;
import java.util.UUID;

public record HideTicketTypeCommand(UUID ticketTypeId) {
    public HideTicketTypeCommand {
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
    }
}
