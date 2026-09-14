package com.biletflow.biletflow.ordercheckout.domain.cart;

import java.util.Objects;
import java.util.UUID;

public record CartId(UUID value) {
    public CartId {
        Objects.requireNonNull(value, "CartId cannot be null");
    }
    public static CartId generate() {
        return new CartId(UUID.randomUUID());
    }
}
