package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

import java.util.Objects;
import java.util.UUID;

public record CheckoutSeatHoldReference(UUID holdId) implements HoldReference {
    public CheckoutSeatHoldReference {
        Objects.requireNonNull(holdId, "holdId cannot be null");
    }
}
