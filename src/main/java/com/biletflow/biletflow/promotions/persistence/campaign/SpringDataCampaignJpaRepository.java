package com.biletflow.biletflow.promotions.persistence.campaign;

import com.biletflow.biletflow.promotions.persistence.campaign.entity.CampaignJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCampaignJpaRepository extends JpaRepository<CampaignJpaEntity, UUID> {
    List<CampaignJpaEntity> findByEventIdOrderByCreatedAtAsc(UUID eventId);
}
