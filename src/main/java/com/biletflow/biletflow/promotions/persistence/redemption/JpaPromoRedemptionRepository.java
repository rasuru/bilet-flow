package com.biletflow.biletflow.promotions.persistence.redemption;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.redemption.CampaignRedemptionStats;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemptionRepository;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import com.biletflow.biletflow.promotions.persistence.redemption.entity.PromoRedemptionJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaPromoRedemptionRepository implements PromoRedemptionRepository {

    private final SpringDataPromoRedemptionJpaRepository jpaRepository;
    private final PromoRedemptionPersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaPromoRedemptionRepository(SpringDataPromoRedemptionJpaRepository jpaRepository, PromoRedemptionPersistenceMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<PromoRedemption> findById(RedemptionId id) {
        Objects.requireNonNull(id, "RedemptionId cannot be null");
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<PromoRedemption> findActiveReservations(CampaignId campaignId, UUID sessionId, Instant now) {
        Objects.requireNonNull(campaignId, "CampaignId cannot be null");
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        return jpaRepository
            .findByCampaignIdAndSessionIdAndStatusAndExpiresAtAfter(campaignId.value(), sessionId, RedemptionStatus.RESERVED, now)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public int countActive(CampaignId campaignId, Instant now) {
        Objects.requireNonNull(campaignId, "CampaignId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        return Math.toIntExact(jpaRepository.countActive(campaignId.value(), RedemptionStatus.CONFIRMED, RedemptionStatus.RESERVED, now));
    }

    @Override
    public Map<CampaignId, CampaignRedemptionStats> statsFor(Collection<CampaignId> campaignIds, Instant now) {
        Objects.requireNonNull(campaignIds, "campaignIds cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        if (campaignIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids = campaignIds.stream().map(CampaignId::value).toList();

        Map<UUID, Long> reserved = new HashMap<>();
        for (Object[] row : jpaRepository.activeReservationCounts(ids, RedemptionStatus.RESERVED, now)) {
            reserved.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        Map<CampaignId, CampaignRedemptionStats> stats = new HashMap<>();
        for (Object[] row : jpaRepository.confirmedTotals(ids, RedemptionStatus.CONFIRMED)) {
            UUID id = (UUID) row[0];
            int confirmed = ((Number) row[1]).intValue();
            stats.put(
                new CampaignId(id),
                new CampaignRedemptionStats(
                    confirmed + Math.toIntExact(reserved.getOrDefault(id, 0L)),
                    confirmed,
                    ((Number) row[2]).intValue(),
                    Money.kzt(toBigDecimal(row[3])),
                    Money.kzt(toBigDecimal(row[4]))
                )
            );
        }

        reserved.forEach((id, count) ->
            stats.putIfAbsent(
                new CampaignId(id),
                new CampaignRedemptionStats(Math.toIntExact(count), 0, 0, Money.zeroKzt(), Money.zeroKzt())
            )
        );

        return stats;
    }

    @Override
    @Transactional
    public PromoRedemption save(PromoRedemption redemption) {
        Objects.requireNonNull(redemption, "redemption cannot be null");

        PromoRedemptionJpaEntity managed = entityManager.find(PromoRedemptionJpaEntity.class, redemption.getId().value());

        if (managed == null) {
            PromoRedemptionJpaEntity created = mapper.toNewEntity(redemption);
            entityManager.persist(created);
            entityManager.flush();
            return mapper.toDomain(created);
        }

        mapper.updateEntity(redemption, managed);
        entityManager.flush();
        return mapper.toDomain(managed);
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
