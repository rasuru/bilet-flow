package com.biletflow.biletflow.promotions.domain.promocode;

import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromoCodeRepository {
    Optional<PromoCode> findById(PromoCodeId id);

    Optional<PromoCode> findByCode(CodeValue code);

    Optional<PromoCode> findByToken(CampaignToken token);

    List<PromoCode> findByCampaignId(CampaignId campaignId);

    List<PromoCode> findByCampaignIds(Collection<CampaignId> campaignIds);

    boolean existsByCode(CodeValue code);

    PromoCode save(PromoCode promoCode);
}
