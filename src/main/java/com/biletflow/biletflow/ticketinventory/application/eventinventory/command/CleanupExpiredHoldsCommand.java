package com.biletflow.biletflow.ticketinventory.application.eventinventory.command;

import java.util.Objects;
import java.util.UUID;

public record CleanupExpiredHoldsCommand(UUID eventId) {
    public CleanupExpiredHoldsCommand {
        Objects.requireNonNull(eventId, "eventId cannot be null");
    }
}
