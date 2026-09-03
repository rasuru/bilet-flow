package com.biletflow.biletflow.ticketinventory.application.eventinventory.command;

import java.util.Objects;
import java.util.UUID;

public record SeatHoldReference(UUID holdId) implements HoldReference {
    public SeatHoldReference {
        Objects.requireNonNull(holdId, "holdId cannot be null");
    }
}
