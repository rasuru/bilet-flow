package com.biletflow.biletflow.promotions.domain.campaign;

import java.util.Objects;
import java.util.UUID;

public record CampaignId(UUID value) {
    public CampaignId {
        Objects.requireNonNull(value, "CampaignId cannot be null");
    }

    public static CampaignId generate() {
        return new CampaignId(UUID.randomUUID());
    }
}
