package com.biletflow.biletflow.promotions.persistence.campaign;

import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignRepository;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import com.biletflow.biletflow.promotions.persistence.campaign.entity.CampaignJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaCampaignRepository implements CampaignRepository {

    private final SpringDataCampaignJpaRepository jpaRepository;
    private final CampaignPersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaCampaignRepository(SpringDataCampaignJpaRepository jpaRepository, CampaignPersistenceMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<Campaign> findById(CampaignId id) {
        Objects.requireNonNull(id, "CampaignId cannot be null");
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Campaign> findByRedemptionId(RedemptionId redemptionId) {
        Objects.requireNonNull(redemptionId, "RedemptionId cannot be null");
        return jpaRepository.findByRedemptionId(redemptionId.value()).map(mapper::toDomain);
    }

    @Override
    public List<Campaign> findByEventId(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return jpaRepository
            .findByEventIdOrderByCreatedAtAsc(eventId)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public Campaign save(Campaign campaign) {
        Objects.requireNonNull(campaign, "campaign cannot be null");

        CampaignJpaEntity managed = entityManager.find(CampaignJpaEntity.class, campaign.getId().value());

        if (managed == null) {
            CampaignJpaEntity created = mapper.toNewEntity(campaign);
            entityManager.persist(created);
            entityManager.flush();
            return mapper.toDomain(created);
        }

        mapper.updateEntity(campaign, managed);
        entityManager.flush();
        return mapper.toDomain(managed);
    }
}
