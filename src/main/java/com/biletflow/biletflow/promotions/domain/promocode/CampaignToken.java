package com.biletflow.biletflow.promotions.domain.promocode;

import java.util.Objects;
import java.util.UUID;

public record CampaignToken(UUID value) {
    public CampaignToken {
        Objects.requireNonNull(value, "CampaignToken cannot be null");
    }

    public static CampaignToken generate() {
        return new CampaignToken(UUID.randomUUID());
    }
}
