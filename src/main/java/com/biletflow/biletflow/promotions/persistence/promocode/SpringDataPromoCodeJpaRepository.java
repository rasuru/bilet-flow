package com.biletflow.biletflow.promotions.persistence.promocode;

import com.biletflow.biletflow.promotions.persistence.promocode.entity.PromoCodeJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPromoCodeJpaRepository extends JpaRepository<PromoCodeJpaEntity, UUID> {
    Optional<PromoCodeJpaEntity> findByCode(String code);

    Optional<PromoCodeJpaEntity> findByCampaignToken(UUID campaignToken);

    List<PromoCodeJpaEntity> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId);

    boolean existsByCode(String code);
}
