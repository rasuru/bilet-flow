package com.biletflow.biletflow.ordercheckout.application.eventmanagement.result;

import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.HoldReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CheckoutHoldInventoryResult(List<HoldReference> holds, Instant expiresAt) {
    public CheckoutHoldInventoryResult {
        Objects.requireNonNull(holds, "holds cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        holds = List.copyOf(holds);
    }
}
