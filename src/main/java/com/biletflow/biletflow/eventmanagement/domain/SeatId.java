package com.biletflow.biletflow.eventmanagement.domain;

import java.util.Objects;
import java.util.UUID;

public record SeatId(UUID value) {
    public SeatId {
        Objects.requireNonNull(value, "SeatId cannot be null");
    }

    public static SeatId generate() {
        return new SeatId(UUID.randomUUID());
    }
}
