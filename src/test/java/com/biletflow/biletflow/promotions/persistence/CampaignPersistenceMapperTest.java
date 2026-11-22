package com.biletflow.biletflow.promotions.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignStatus;
import com.biletflow.biletflow.promotions.domain.campaign.Discount;
import com.biletflow.biletflow.promotions.domain.campaign.DiscountQuote;
import com.biletflow.biletflow.promotions.domain.campaign.RedemptionLimit;
import com.biletflow.biletflow.promotions.domain.campaign.ValidityPeriod;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionSource;
import com.biletflow.biletflow.promotions.domain.redemption.ReservationLifetime;
import com.biletflow.biletflow.promotions.persistence.campaign.CampaignPersistenceMapper;
import com.biletflow.biletflow.promotions.persistence.campaign.entity.CampaignJpaEntity;
import com.biletflow.biletflow.promotions.persistence.promocode.PromoCodePersistenceMapper;
import com.biletflow.biletflow.promotions.persistence.promocode.entity.PromoCodeJpaEntity;
import com.biletflow.biletflow.promotions.persistence.redemption.PromoRedemptionPersistenceMapper;
import com.biletflow.biletflow.promotions.persistence.redemption.entity.PromoRedemptionJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignPersistenceMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private final CampaignPersistenceMapper campaignMapper = new CampaignPersistenceMapper();
    private final PromoCodePersistenceMapper promoCodeMapper = new PromoCodePersistenceMapper();
    private final PromoRedemptionPersistenceMapper redemptionMapper = new PromoRedemptionPersistenceMapper();
    private final UUID vip = UUID.randomUUID();

    private Campaign campaign() {
        return Campaign.create(
            UUID.randomUUID(),
            "Student week",
            Discount.percentage(new BigDecimal("12.5")),
            new ValidityPeriod(NOW.minusSeconds(3_600), NOW.plusSeconds(86_400)),
            Set.of(vip),
            RedemptionLimit.of(10),
            NOW
        );
    }

    @Test
    void campaignSurvivesARoundTrip() {
        Campaign original = campaign();

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
    }

    @Test
    void updatingAnEntityCopiesTheChangedState() {
        Campaign campaign = campaign();
        CampaignJpaEntity entity = campaignMapper.toNewEntity(campaign);

        campaign.end(NOW.plusSeconds(120));
        campaignMapper.updateEntity(campaign, entity);

        assertEquals(CampaignStatus.ENDED, entity.getStatus());
        assertEquals(NOW.plusSeconds(120), entity.getEndedAt());
    }

    @Test
    void entityCannotBeUpdatedFromADifferentCampaign() {
        CampaignJpaEntity entity = campaignMapper.toNewEntity(campaign());

        assertThrows(IllegalArgumentException.class, () -> campaignMapper.updateEntity(campaign(), entity));
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
        PromoCode promoCode = PromoCode.generateFor(campaign().getId(), NOW);
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

    @Test
    void redemptionSurvivesARoundTripIncludingConfirmation() {
        Campaign campaign = campaign();
        PromoCode promoCode = PromoCode.generateFor(campaign.getId(), NOW);
        PromoRedemption redemption = PromoRedemption.reserve(
            campaign.getId(),
            promoCode.getId(),
            UUID.randomUUID(),
            RedemptionSource.CAMPAIGN_LINK,
            new DiscountQuote(Money.kzt(20_000), 2, Money.kzt(2_500)),
            NOW,
            ReservationLifetime.defaultLifetime()
        );
        PromoRedemptionJpaEntity entity = redemptionMapper.toNewEntity(redemption);

        UUID order = UUID.randomUUID();
        redemption.confirm(order, NOW.plusSeconds(30));
        redemptionMapper.updateEntity(redemption, entity);
        PromoRedemption restored = redemptionMapper.toDomain(entity);

        assertEquals(redemption.getId(), restored.getId());
        assertEquals(campaign.getId(), restored.getCampaignId());
        assertEquals(promoCode.getId(), restored.getPromoCodeId());
        assertEquals(redemption.getSessionId(), restored.getSessionId());
        assertEquals(RedemptionSource.CAMPAIGN_LINK, restored.getSource());
        assertEquals(2, restored.getTicketQuantity());
        assertEquals(Money.kzt(20_000), restored.getGrossAmount());
        assertEquals(Money.kzt(2_500), restored.getDiscountAmount());
        assertEquals(redemption.getExpiresAt(), restored.getExpiresAt());
        assertEquals(order, restored.getOrderId());
        assertEquals(NOW.plusSeconds(30), restored.getConfirmedAt());
    }
}
