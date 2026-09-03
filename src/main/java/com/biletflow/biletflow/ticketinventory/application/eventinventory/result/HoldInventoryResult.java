package com.biletflow.biletflow.ticketinventory.application.eventinventory.result;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record HoldInventoryResult(List<HoldReference> holds, Instant expiresAt) {
    public HoldInventoryResult {
        Objects.requireNonNull(holds, "holds cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        holds = List.copyOf(holds);
    }
}
