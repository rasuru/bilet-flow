package com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga;

import java.util.Objects;
import java.util.UUID;

public record InventoryReservationId(UUID value) {
    public InventoryReservationId {
        Objects.requireNonNull(value, "InventoryReservationId value cannot be null");
    }

    public static InventoryReservationId generate() {
        return new InventoryReservationId(UUID.randomUUID());
    }
}
