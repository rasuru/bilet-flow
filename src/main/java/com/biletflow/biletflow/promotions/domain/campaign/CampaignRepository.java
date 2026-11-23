package com.biletflow.biletflow.promotions.domain.campaign;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository {
    Optional<Campaign> findById(CampaignId id);

    Optional<Campaign> findByIdForUpdate(CampaignId id);

    List<Campaign> findByEventId(UUID eventId);

    Campaign save(Campaign campaign);
}
