package com.biletflow.biletflow.promotions.domain.campaign;

import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository {
    Optional<Campaign> findById(CampaignId id);

    Optional<Campaign> findByRedemptionId(RedemptionId redemptionId);

    List<Campaign> findByEventId(UUID eventId);

    Campaign save(Campaign campaign);
}
