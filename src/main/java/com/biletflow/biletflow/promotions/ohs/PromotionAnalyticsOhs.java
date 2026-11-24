package com.biletflow.biletflow.promotions.ohs;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PromotionAnalyticsOhs {
    List<Redemption> confirmedRedemptions(UUID eventId);

    record Redemption(UUID orderId, UUID campaignId, String campaign, String code, Set<UUID> eligibleTypes) {
        public Redemption {
            eligibleTypes = Set.copyOf(eligibleTypes);
        }

        public boolean appliesTo(UUID ticketTypeId) {
            return eligibleTypes.isEmpty() || eligibleTypes.contains(ticketTypeId);
        }
    }
}
