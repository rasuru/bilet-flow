package com.biletflow.biletflow.promotions.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignStatus;
import com.biletflow.biletflow.promotions.domain.campaign.CheckoutLine;
import com.biletflow.biletflow.promotions.domain.campaign.Discount;
import com.biletflow.biletflow.promotions.domain.campaign.RedemptionLimit;
import com.biletflow.biletflow.promotions.domain.campaign.ValidityPeriod;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.persistence.campaign.CampaignPersistenceMapper;
import com.biletflow.biletflow.promotions.persistence.campaign.entity.CampaignJpaEntity;
import com.biletflow.biletflow.promotions.persistence.promocode.PromoCodePersistenceMapper;
import com.biletflow.biletflow.promotions.persistence.promocode.entity.PromoCodeJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignPersistenceMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final CampaignPersistenceMapper campaignMapper = new CampaignPersistenceMapper();
    private final PromoCodePersistenceMapper promoCodeMapper = new PromoCodePersistenceMapper();
    private final UUID vip = UUID.randomUUID();

    private Campaign campaignWithRedemptions() {
        Campaign campaign = Campaign.create(
            UUID.randomUUID(),
            "Student week",
            Discount.percentage(new BigDecimal("12.5")),
            new ValidityPeriod(NOW.minusSeconds(3_600), NOW.plusSeconds(86_400)),
            Set.of(vip),
            RedemptionLimit.of(10),
            NOW
        );
        List<CheckoutLine> lines = List.of(new CheckoutLine(vip, Money.kzt(10_000), 1));

        PromoRedemption confirmed = campaign.reserveRedemption(UUID.randomUUID(), lines, NOW, NOW.plusSeconds(600));
        campaign.confirmRedemption(confirmed.getId(), UUID.randomUUID(), NOW.plusSeconds(60));
        campaign.reserveRedemption(UUID.randomUUID(), lines, NOW, NOW.plusSeconds(600));
        return campaign;
    }

    @Test
    void campaignSurvivesARoundTrip() {
        Campaign original = campaignWithRedemptions();

        Campaign restored = campaignMapper.toDomain(campaignMapper.toNewEntity(original));

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getEventId(), restored.getEventId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getDiscount(), restored.getDiscount());
        assertEquals(original.getValidity(), restored.getValidity());
        assertEquals(Set.of(vip), restored.getApplicableTicketTypeIds());
        assertEquals(original.getRedemptionLimit(), restored.getRedemptionLimit());
        assertEquals(CampaignStatus.ACTIVE, restored.getStatus());
        assertEquals(NOW, restored.getCreatedAt());
        assertEquals(2, restored.getRedemptions().size());
        assertEquals(1, restored.confirmedRedemptionCount());
        assertEquals(8, restored.remainingRedemptionsAt(NOW));
    }

    @Test
    void redemptionDetailsSurviveARoundTrip() {
        Campaign original = campaignWithRedemptions();

        Campaign restored = campaignMapper.toDomain(campaignMapper.toNewEntity(original));

        for (int i = 0; i < original.getRedemptions().size(); i++) {
            PromoRedemption expected = original.getRedemptions().get(i);
            PromoRedemption actual = restored.getRedemptions().get(i);

            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getSessionId(), actual.getSessionId());
            assertEquals(expected.getDiscountAmount(), actual.getDiscountAmount());
            assertEquals(expected.getReservedAt(), actual.getReservedAt());
            assertEquals(expected.getExpiresAt(), actual.getExpiresAt());
            assertEquals(expected.getOrderId(), actual.getOrderId());
            assertEquals(expected.getStatus(), actual.getStatus());
            assertEquals(expected.getConfirmedAt(), actual.getConfirmedAt());
        }
    }

    @Test
    void updatingAnEntityCopiesTheChangedState() {
        Campaign campaign = campaignWithRedemptions();
        CampaignJpaEntity entity = campaignMapper.toNewEntity(campaign);

        campaign.end(NOW.plusSeconds(120));
        campaignMapper.updateEntity(campaign, entity);

        assertEquals(CampaignStatus.ENDED, entity.getStatus());
        assertEquals(NOW.plusSeconds(120), entity.getEndedAt());
        assertEquals(2, entity.getRedemptions().size());
    }

    @Test
    void entityCannotBeUpdatedFromADifferentCampaign() {
        CampaignJpaEntity entity = campaignMapper.toNewEntity(campaignWithRedemptions());

        assertThrows(IllegalArgumentException.class, () -> campaignMapper.updateEntity(campaignWithRedemptions(), entity));
    }

    @Test
    void unlimitedCampaignKeepsItsNullLimit() {
        Campaign campaign = Campaign.create(
            UUID.randomUUID(),
            "Open",
            Discount.fixedKzt(Money.kzt(1_000)),
            new ValidityPeriod(NOW, NOW.plusSeconds(60)),
            Set.of(),
            RedemptionLimit.unlimited(),
            NOW
        );

        Campaign restored = campaignMapper.toDomain(campaignMapper.toNewEntity(campaign));

        assertTrue(restored.getRedemptionLimit().isUnlimited());
        assertTrue(restored.getApplicableTicketTypeIds().isEmpty());
        assertEquals(Discount.fixedKzt(Money.kzt(1_000)), restored.getDiscount());
    }

    @Test
    void promoCodeSurvivesARoundTripIncludingDisabledState() {
        PromoCode promoCode = PromoCode.generateFor(campaignWithRedemptions().getId(), NOW);
        promoCode.disable(NOW.plusSeconds(5));

        PromoCodeJpaEntity entity = promoCodeMapper.toNewEntity(promoCode);
        PromoCode restored = promoCodeMapper.toDomain(entity);

        assertEquals(promoCode.getId(), restored.getId());
        assertEquals(promoCode.getCampaignId(), restored.getCampaignId());
        assertEquals(promoCode.getCode(), restored.getCode());
        assertEquals(promoCode.getToken(), restored.getToken());
        assertFalse(restored.isEnabled());
        assertEquals(NOW.plusSeconds(5), restored.getDisabledAt());
    }
}
