package com.biletflow.biletflow.promotions.persistence.campaign;

import com.biletflow.biletflow.promotions.persistence.campaign.entity.CampaignJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataCampaignJpaRepository extends JpaRepository<CampaignJpaEntity, UUID> {
    List<CampaignJpaEntity> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    @Query("select c from CampaignJpaEntity c join c.redemptions r where r.redemptionId = :redemptionId")
    Optional<CampaignJpaEntity> findByRedemptionId(@Param("redemptionId") UUID redemptionId);
}
