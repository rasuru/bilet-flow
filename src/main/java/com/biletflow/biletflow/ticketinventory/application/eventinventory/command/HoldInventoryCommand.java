package com.biletflow.biletflow.ticketinventory.application.eventinventory.command;

import java.util.Objects;
import java.util.UUID;

public record HoldInventoryCommand(UUID eventId, UUID ticketTypeId, String sessionId, HoldSelection selection) {
    public HoldInventoryCommand {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(selection, "selection cannot be null");

        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
    }
}
