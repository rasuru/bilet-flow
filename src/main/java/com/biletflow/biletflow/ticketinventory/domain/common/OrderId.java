package com.biletflow.biletflow.ticketinventory.domain.common;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {
    public OrderId {
        Objects.requireNonNull(value, "OrderId value cannot be null");
    }
}
