package com.biletflow.biletflow.promotions.persistence.redemption;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeId;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import com.biletflow.biletflow.promotions.persistence.redemption.entity.PromoRedemptionJpaEntity;
import java.util.Currency;
import org.springframework.stereotype.Component;

@Component
public class PromoRedemptionPersistenceMapper {

    public PromoRedemption toDomain(PromoRedemptionJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());

        return PromoRedemption.rehydrate(
            new RedemptionId(entity.getId()),
            new CampaignId(entity.getCampaignId()),
            new PromoCodeId(entity.getPromoCodeId()),
            entity.getSessionId(),
            entity.getSource(),
            entity.getTicketQuantity(),
            new Money(entity.getGrossAmount(), currency),
            new Money(entity.getDiscountAmount(), currency),
            entity.getReservedAt(),
            entity.getExpiresAt(),
            entity.getOrderId(),
            entity.getStatus(),
            entity.getConfirmedAt()
        );
    }

    public PromoRedemptionJpaEntity toNewEntity(PromoRedemption domain) {
        if (!domain.getGrossAmount().currency().equals(domain.getDiscountAmount().currency())) {
            throw new IllegalArgumentException("Gross and discount amounts must use the same currency");
        }

        PromoRedemptionJpaEntity entity = new PromoRedemptionJpaEntity(
            domain.getId().value(),
            domain.getCampaignId().value(),
            domain.getPromoCodeId().value(),
            domain.getSessionId(),
            domain.getSource(),
            domain.getTicketQuantity(),
            domain.getGrossAmount().amount(),
            domain.getDiscountAmount().amount(),
            domain.getGrossAmount().currency().getCurrencyCode(),
            domain.getReservedAt(),
            domain.getExpiresAt()
        );
        updateEntity(domain, entity);
        return entity;
    }

    public void updateEntity(PromoRedemption domain, PromoRedemptionJpaEntity entity) {
        if (!entity.getId().equals(domain.getId().value())) {
            throw new IllegalArgumentException("Cannot map onto a different PromoRedemption id");
        }

        entity.replaceState(domain.getStatus(), domain.getOrderId(), domain.getConfirmedAt());
    }
}
