package com.biletflow.biletflow.promotions.persistence.campaign;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.Discount;
import com.biletflow.biletflow.promotions.domain.campaign.RedemptionLimit;
import com.biletflow.biletflow.promotions.domain.campaign.ValidityPeriod;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import com.biletflow.biletflow.promotions.persistence.campaign.entity.CampaignJpaEntity;
import com.biletflow.biletflow.promotions.persistence.campaign.entity.PromoRedemptionJpaEmbeddable;
import java.util.Currency;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CampaignPersistenceMapper {

    public Campaign toDomain(CampaignJpaEntity entity) {
        return Campaign.rehydrate(
            new CampaignId(entity.getId()),
            entity.getEventId(),
            entity.getName(),
            new Discount(entity.getDiscountType(), entity.getDiscountValue()),
            new ValidityPeriod(entity.getValidFrom(), entity.getValidUntil()),
            Set.copyOf(entity.getApplicableTicketTypeIds()),
            new RedemptionLimit(entity.getMaxRedemptions()),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getEndedAt(),
            entity.getRedemptions().stream().map(this::toDomain).toList()
        );
    }

    public CampaignJpaEntity toNewEntity(Campaign domain) {
        CampaignJpaEntity entity = new CampaignJpaEntity(
            domain.getId().value(),
            domain.getEventId(),
            domain.getName(),
            domain.getDiscount().type(),
            domain.getDiscount().value(),
            domain.getValidity().startsAt(),
            domain.getValidity().endsAt(),
            domain.getRedemptionLimit().maxRedemptions(),
            domain.getCreatedAt(),
            domain.getApplicableTicketTypeIds()
        );
        updateEntity(domain, entity);
        return entity;
    }

    public void updateEntity(Campaign domain, CampaignJpaEntity entity) {
        if (!entity.getId().equals(domain.getId().value())) {
            throw new IllegalArgumentException("Cannot map onto a different Campaign id");
        }

        entity.replaceState(domain.getStatus(), domain.getEndedAt(), domain.getRedemptions().stream().map(this::toEmbeddable).toList());
    }

    private PromoRedemption toDomain(PromoRedemptionJpaEmbeddable embeddable) {
        return PromoRedemption.rehydrate(
            new RedemptionId(embeddable.getRedemptionId()),
            embeddable.getSessionId(),
            new Money(embeddable.getDiscountAmount(), Currency.getInstance(embeddable.getDiscountCurrency())),
            embeddable.getReservedAt(),
            embeddable.getExpiresAt(),
            embeddable.getOrderId(),
            embeddable.getStatus(),
            embeddable.getConfirmedAt()
        );
    }

    private PromoRedemptionJpaEmbeddable toEmbeddable(PromoRedemption redemption) {
        return new PromoRedemptionJpaEmbeddable(
            redemption.getId().value(),
            redemption.getSessionId(),
            redemption.getDiscountAmount().amount(),
            redemption.getDiscountAmount().currency().getCurrencyCode(),
            redemption.getReservedAt(),
            redemption.getExpiresAt(),
            redemption.getOrderId(),
            redemption.getStatus(),
            redemption.getConfirmedAt()
        );
    }
}
