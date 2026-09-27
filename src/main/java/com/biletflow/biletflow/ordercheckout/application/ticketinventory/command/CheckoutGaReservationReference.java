package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

import java.util.Objects;
import java.util.UUID;

public record CheckoutGaReservationReference(UUID reservationId) implements CheckoutHoldReference {
    public CheckoutGaReservationReference {
        Objects.requireNonNull(reservationId, "reservationId cannot be null");
    }
}
