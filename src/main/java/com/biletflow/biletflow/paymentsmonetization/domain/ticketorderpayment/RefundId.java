package com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment;

import java.util.Objects;
import java.util.UUID;

public record RefundId(UUID value) {
    public RefundId {
        Objects.requireNonNull(value, "RefundId cannot be null");
    }

    public static RefundId generate() {
        return new RefundId(UUID.randomUUID());
    }
}
