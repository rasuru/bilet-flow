package com.biletflow.biletflow.ticketinventory.application.eventinventory.command;

import java.util.Objects;
import java.util.UUID;

public record GaReservationReference(UUID reservationId) implements HoldReference {
    public GaReservationReference {
        Objects.requireNonNull(reservationId, "reservationId cannot be null");
    }
}
