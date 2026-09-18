package com.biletflow.biletflow.ordercheckout.domain.order;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {
    public OrderId {
        Objects.requireNonNull(value, "OrderId cannot be null");
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }
}
