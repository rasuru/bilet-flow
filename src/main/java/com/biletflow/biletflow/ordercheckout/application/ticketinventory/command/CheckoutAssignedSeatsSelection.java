package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CheckoutAssignedSeatsSelection(Set<UUID> seatIds) implements CheckoutHoldSelection {
    public CheckoutAssignedSeatsSelection {
        Objects.requireNonNull(seatIds, "seatIds cannot be null");
        seatIds = Set.copyOf(seatIds);

        if (seatIds.isEmpty()) {
            throw new IllegalArgumentException("At least one seat must be selected");
        }

        if (seatIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("seatIds cannot contain null");
        }
    }
}
