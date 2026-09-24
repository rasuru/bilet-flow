package com.biletflow.biletflow.payments.domain.payment;

import java.util.Objects;
import java.util.UUID;

public record RefundId(UUID value) {
    public RefundId {
        Objects.requireNonNull(value, "RefundId cannot be null");
    }

    public static RefundId generate(){
        return new RefundId(UUID.randomUUID());
    }
}
