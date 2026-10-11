package com.biletflow.biletflow.paymentsmonetization.domain.payout;

import java.util.Objects;
import java.util.UUID;

public record PayoutId(UUID value) {
    public PayoutId {
        Objects.requireNonNull(value, "PayoutId cannot be null");
    }

    public static PayoutId generate() {
        return new PayoutId(UUID.randomUUID());
    }
}
