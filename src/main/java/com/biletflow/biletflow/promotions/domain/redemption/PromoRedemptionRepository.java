package com.biletflow.biletflow.promotions.domain.redemption;

import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PromoRedemptionRepository {
    Optional<PromoRedemption> findById(RedemptionId id);

    List<PromoRedemption> findActiveReservations(CampaignId campaignId, UUID sessionId, Instant now);

    int countActive(CampaignId campaignId, Instant now);

    Map<CampaignId, CampaignRedemptionStats> statsFor(Collection<CampaignId> campaignIds, Instant now);

    PromoRedemption save(PromoRedemption redemption);
}
