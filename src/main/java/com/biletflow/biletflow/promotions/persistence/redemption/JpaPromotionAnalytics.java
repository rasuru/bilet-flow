package com.biletflow.biletflow.promotions.persistence.redemption;

import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import com.biletflow.biletflow.promotions.ohs.PromotionAnalyticsOhs;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPromotionAnalytics implements PromotionAnalyticsOhs {

    private final EntityManager entities;

    public JpaPromotionAnalytics(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public List<Redemption> confirmedRedemptions(UUID eventId) {
        var eligibleTypes = new HashMap<UUID, Set<UUID>>();
        entities
            .createQuery(
                """
                select c.id, typeId from CampaignJpaEntity c join c.applicableTicketTypeIds typeId
                where c.eventId = :eventId
                """,
                EligibleType.class
            )
            .setParameter("eventId", eventId)
            .getResultList()
            .forEach(type -> eligibleTypes.computeIfAbsent(type.campaignId(), ignored -> new HashSet<>()).add(type.ticketTypeId()));

        return entities
            .createQuery(
                """
                select r.orderId, c.id, c.name, p.code from PromoRedemptionJpaEntity r
                join CampaignJpaEntity c on c.id = r.campaignId
                join PromoCodeJpaEntity p on p.id = r.promoCodeId
                where c.eventId = :eventId and r.status = :confirmed and r.orderId is not null
                """,
                ConfirmedCode.class
            )
            .setParameter("eventId", eventId)
            .setParameter("confirmed", RedemptionStatus.CONFIRMED)
            .getResultList()
            .stream()
            .map(row ->
                new Redemption(
                    row.orderId(),
                    row.campaignId(),
                    row.name(),
                    row.code(),
                    eligibleTypes.getOrDefault(row.campaignId(), Set.of())
                )
            )
            .toList();
    }

    public record EligibleType(UUID campaignId, UUID ticketTypeId) {}

    public record ConfirmedCode(UUID orderId, UUID campaignId, String name, String code) {}
}
