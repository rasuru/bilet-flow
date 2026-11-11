package com.biletflow.biletflow.promotions.application.campaign.view;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.application.promocode.view.PromoCodeView;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignStatus;
import com.biletflow.biletflow.promotions.domain.campaign.DiscountType;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CampaignView(
    UUID id,
    UUID eventId,
    String name,
    DiscountType discountType,
    BigDecimal discountValue,
    Instant validFrom,
    Instant validUntil,
    Set<UUID> applicableTicketTypeIds,
    Integer maxRedemptions,
    int usedRedemptions,
    int confirmedRedemptions,
    Integer remainingRedemptions,
    Money totalDiscountGiven,
    CampaignStatus status,
    Instant createdAt,
    Instant endedAt,
    List<PromoCodeView> promoCodes
) {
    public static CampaignView from(Campaign campaign, List<PromoCode> promoCodes, Instant now) {
        Money totalDiscountGiven = campaign
            .getRedemptions()
            .stream()
            .filter(redemption -> redemption.getStatus() == RedemptionStatus.CONFIRMED)
            .map(PromoRedemption::getDiscountAmount)
            .reduce(Money.zeroKzt(), Money::add);

        return new CampaignView(
            campaign.getId().value(),
            campaign.getEventId(),
            campaign.getName(),
            campaign.getDiscount().type(),
            campaign.getDiscount().value(),
            campaign.getValidity().startsAt(),
            campaign.getValidity().endsAt(),
            campaign.getApplicableTicketTypeIds(),
            campaign.getRedemptionLimit().maxRedemptions(),
            campaign.usedRedemptionsAt(now),
            campaign.confirmedRedemptionCount(),
            campaign.remainingRedemptionsAt(now),
            totalDiscountGiven,
            campaign.getStatus(),
            campaign.getCreatedAt(),
            campaign.getEndedAt(),
            promoCodes.stream().map(PromoCodeView::from).toList()
        );
    }
}
