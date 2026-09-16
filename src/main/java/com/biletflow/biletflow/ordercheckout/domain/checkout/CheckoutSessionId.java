package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.util.Objects;
import java.util.UUID;

public record CheckoutSessionId(UUID value) {
    public CheckoutSessionId {
        Objects.requireNonNull(value, "CheckoutSessionId can not be null!");
    }

    public static CheckoutSessionId generate() {
        return return new CheckoutSessionId(UUID.randomUUID());
    }
}
