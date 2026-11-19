package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public class InventoryUnavailableException extends IllegalStateException {

    private final @Nullable UUID seatId;

    public InventoryUnavailableException(String message, @Nullable UUID seatId) {
        super(message);
        this.seatId = seatId;
    }

    public @Nullable UUID seatId() {
        return seatId;
    }
}
