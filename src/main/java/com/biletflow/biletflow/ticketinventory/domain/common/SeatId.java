package com.biletflow.biletflow.ticketinventory.domain.common;

import java.util.Objects;
import java.util.UUID;

public record SeatId(UUID value) {
    public SeatId {
        Objects.requireNonNull(value, "SeatId value cannot be null");
    }
}
