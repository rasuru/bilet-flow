package com.biletflow.biletflow.promotions.persistence.redemption;

import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import com.biletflow.biletflow.promotions.persistence.redemption.entity.PromoRedemptionJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataPromoRedemptionJpaRepository extends JpaRepository<PromoRedemptionJpaEntity, UUID> {
    List<PromoRedemptionJpaEntity> findByCampaignIdAndSessionIdAndStatusAndExpiresAtAfter(
        UUID campaignId,
        UUID sessionId,
        RedemptionStatus status,
        Instant now
    );

    @Query(
        """
        select count(r) from PromoRedemptionJpaEntity r
        where r.campaignId = :campaignId
          and (r.status = :confirmed or (r.status = :reserved and r.expiresAt > :now))
        """
    )
    long countActive(
        @Param("campaignId") UUID campaignId,
        @Param("confirmed") RedemptionStatus confirmed,
        @Param("reserved") RedemptionStatus reserved,
        @Param("now") Instant now
    );

    @Query(
        """
        select r.campaignId, count(r), sum(r.ticketQuantity), sum(r.grossAmount), sum(r.discountAmount)
        from PromoRedemptionJpaEntity r
        where r.campaignId in :campaignIds and r.status = :confirmed
        group by r.campaignId
        """
    )
    List<Object[]> confirmedTotals(@Param("campaignIds") Collection<UUID> campaignIds, @Param("confirmed") RedemptionStatus confirmed);

    @Query(
        """
        select r.campaignId, count(r)
        from PromoRedemptionJpaEntity r
        where r.campaignId in :campaignIds and r.status = :reserved and r.expiresAt > :now
        group by r.campaignId
        """
    )
    List<Object[]> activeReservationCounts(
        @Param("campaignIds") Collection<UUID> campaignIds,
        @Param("reserved") RedemptionStatus reserved,
        @Param("now") Instant now
    );
}
