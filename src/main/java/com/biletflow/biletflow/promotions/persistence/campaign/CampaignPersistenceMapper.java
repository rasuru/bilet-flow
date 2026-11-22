package com.biletflow.biletflow.promotions.persistence.campaign;

import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.Discount;
import com.biletflow.biletflow.promotions.domain.campaign.RedemptionLimit;
import com.biletflow.biletflow.promotions.domain.campaign.ValidityPeriod;
import com.biletflow.biletflow.promotions.persistence.campaign.entity.CampaignJpaEntity;
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
            entity.getEndedAt()
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

        entity.replaceState(domain.getStatus(), domain.getEndedAt());
    }
}
