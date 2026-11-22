package com.biletflow.biletflow.promotions.persistence.promocode;

import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.promocode.CampaignToken;
import com.biletflow.biletflow.promotions.domain.promocode.CodeValue;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeId;
import com.biletflow.biletflow.promotions.persistence.promocode.entity.PromoCodeJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PromoCodePersistenceMapper {

    public PromoCode toDomain(PromoCodeJpaEntity entity) {
        return PromoCode.rehydrate(
            new PromoCodeId(entity.getId()),
            new CampaignId(entity.getCampaignId()),
            new CodeValue(entity.getCode()),
            new CampaignToken(entity.getCampaignToken()),
            entity.getCreatedAt(),
            entity.isEnabled(),
            entity.getDisabledAt()
        );
    }

    public PromoCodeJpaEntity toNewEntity(PromoCode domain) {
        PromoCodeJpaEntity entity = new PromoCodeJpaEntity(
            domain.getId().value(),
            domain.getCampaignId().value(),
            domain.getCode().value(),
            domain.getToken().value(),
            domain.getCreatedAt()
        );
        updateEntity(domain, entity);
        return entity;
    }

    public void updateEntity(PromoCode domain, PromoCodeJpaEntity entity) {
        if (!entity.getId().equals(domain.getId().value())) {
            throw new IllegalArgumentException("Cannot map onto a different PromoCode id");
        }

        entity.replaceState(domain.isEnabled(), domain.getDisabledAt());
    }
}
