package com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned;

import java.util.Objects;
import java.util.UUID;

public record SeatHoldId(UUID value) {
    public SeatHoldId {
        Objects.requireNonNull(value, "SeatHoldId value cannot be null");
    }

    public static SeatHoldId generate() {
        return new SeatHoldId(UUID.randomUUID());
    }
}
