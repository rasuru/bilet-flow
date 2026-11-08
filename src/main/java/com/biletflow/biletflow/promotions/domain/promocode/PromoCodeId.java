package com.biletflow.biletflow.promotions.domain.promocode;

import java.util.Objects;
import java.util.UUID;

public record PromoCodeId(UUID value) {
    public PromoCodeId {
        Objects.requireNonNull(value, "PromoCodeId cannot be null");
    }

    public static PromoCodeId generate() {
        return new PromoCodeId(UUID.randomUUID());
    }
}
