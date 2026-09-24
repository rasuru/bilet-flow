package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

import java.util.Objects;
import java.util.UUID;

public record CheckoutGaReservationReference(UUID reservationId) implements HoldReference {
    public GaReservationReference {
        Objects.requireNonNull(reservationId, "reservationId cannot be null");
    }
}
