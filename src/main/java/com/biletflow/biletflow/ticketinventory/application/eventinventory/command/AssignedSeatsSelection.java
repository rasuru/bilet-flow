package com.biletflow.biletflow.ticketinventory.application.eventinventory.command;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AssignedSeatsSelection(Set<UUID> seatIds) implements HoldSelection {
    public AssignedSeatsSelection {
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
