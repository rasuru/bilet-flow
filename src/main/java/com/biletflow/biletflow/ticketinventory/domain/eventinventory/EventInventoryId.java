package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

import java.util.Objects;
import java.util.UUID;

public record EventInventoryId(UUID value) {
    public EventInventoryId {
        Objects.requireNonNull(value, "EventInventoryId cannot be null");
    }

    public static EventInventoryId generate() {
        return new EventInventoryId(UUID.randomUUID());
    }
}
