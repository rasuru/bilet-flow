package com.biletflow.biletflow.ordercheckout.application.ticketinventory.result;

import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.CheckoutHoldReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CheckoutHoldInventoryResult(List<CheckoutHoldReference> holds, Instant expiresAt) {
    public CheckoutHoldInventoryResult {
        Objects.requireNonNull(holds, "holds cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        holds = List.copyOf(holds);
    }
}
