package com.biletflow.biletflow.ticketinventory.application.eventinventory.view;

import java.util.Objects;
import java.util.UUID;

public record SeatAvailabilityView(UUID seatId, SeatAvailabilityStatus status) {
    public SeatAvailabilityView {
        Objects.requireNonNull(seatId, "seatId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }
}
