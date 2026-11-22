package com.biletflow.biletflow.promotions.domain.redemption;

import java.util.Objects;
import java.util.UUID;

public record RedemptionId(UUID value) {
    public RedemptionId {
        Objects.requireNonNull(value, "RedemptionId cannot be null");
    }

    public static RedemptionId generate() {
        return new RedemptionId(UUID.randomUUID());
    }
}
