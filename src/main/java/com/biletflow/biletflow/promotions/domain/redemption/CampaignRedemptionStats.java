package com.biletflow.biletflow.promotions.domain.redemption;

import com.biletflow.biletflow.common.domain.Money;
import java.util.Objects;

public record CampaignRedemptionStats(
    int usedRedemptions,
    int confirmedRedemptions,
    int ticketsSold,
    Money grossRevenue,
    Money discountGiven
) {
    public CampaignRedemptionStats {
        Objects.requireNonNull(grossRevenue, "grossRevenue cannot be null");
        Objects.requireNonNull(discountGiven, "discountGiven cannot be null");

        if (usedRedemptions < 0 || confirmedRedemptions < 0 || ticketsSold < 0) {
            throw new IllegalArgumentException("Redemption counts cannot be negative");
        }
    }

    public static CampaignRedemptionStats empty() {
        return new CampaignRedemptionStats(0, 0, 0, Money.zeroKzt(), Money.zeroKzt());
    }

    public Money netRevenue() {
        return grossRevenue.subtract(discountGiven);
    }
}
