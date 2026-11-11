package com.biletflow.biletflow.promotions.application.promocode.view;

import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import java.time.Instant;
import java.util.UUID;

public record PromoCodeView(
    UUID id,
    UUID campaignId,
    String code,
    UUID campaignToken,
    boolean enabled,
    Instant createdAt,
    Instant disabledAt
) {
    public static PromoCodeView from(PromoCode promoCode) {
        return new PromoCodeView(
            promoCode.getId().value(),
            promoCode.getCampaignId().value(),
            promoCode.getCode().value(),
            promoCode.getToken().value(),
            promoCode.isEnabled(),
            promoCode.getCreatedAt(),
            promoCode.getDisabledAt()
        );
    }
}
