package com.biletflow.biletflow.ticketinventory.application.eventinventory.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReleaseHoldCommand(UUID eventId, String sessionId, List<HoldReference> holds) {
    public ReleaseHoldCommand {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(holds, "holds cannot be null");

        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }

        holds = List.copyOf(holds);

        if (holds.isEmpty()) {
            throw new IllegalArgumentException("At least one hold is required");
        }
    }
}
