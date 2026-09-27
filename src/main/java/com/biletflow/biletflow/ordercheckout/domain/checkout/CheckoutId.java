package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.util.Objects;
import java.util.UUID;


public record CheckoutId(UUID value) {
    public CheckoutId {
        Objects.requireNonNull(value, "CheckoutId cannot be null");
    }
    public static CheckoutId generate() {
        return new CheckoutId(UUID.randomUUID());
    }
}
