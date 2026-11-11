package com.biletflow.biletflow.promotions.application;

import static com.biletflow.biletflow.promotions.application.PromotionsTestFixture.NOW;
import static com.biletflow.biletflow.promotions.application.PromotionsTestFixture.STRANGER;
import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditType;
import com.biletflow.biletflow.promotions.application.campaign.command.CreateCampaignCommand;
import com.biletflow.biletflow.promotions.application.campaign.command.EndCampaignCommand;
import com.biletflow.biletflow.promotions.application.campaign.view.CampaignView;
import com.biletflow.biletflow.promotions.application.promocode.view.PromoCodeView;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignStatus;
import com.biletflow.biletflow.promotions.domain.campaign.DiscountType;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignServiceTest {

    private final PromotionsTestFixture fixture = new PromotionsTestFixture();

    @Test
    void organizerCreatesCampaignWithAGeneratedPromoCode() {
        CampaignView campaign = fixture.createTenPercentOnVip(100);

        assertEquals(fixture.eventId, campaign.eventId());
        assertEquals("Student week", campaign.name());
        assertEquals(DiscountType.PERCENTAGE, campaign.discountType());
        assertEquals(Set.of(fixture.vip), campaign.applicableTicketTypeIds());
        assertEquals(CampaignStatus.ACTIVE, campaign.status());
        assertEquals(100, campaign.remainingRedemptions());
        assertEquals(Money.zeroKzt(), campaign.totalDiscountGiven());
        assertEquals(NOW, campaign.createdAt());

        assertEquals(1, campaign.promoCodes().size());
        PromoCodeView promoCode = campaign.promoCodes().get(0);
        assertEquals(8, promoCode.code().length());
        assertNotNull(promoCode.campaignToken());
        assertTrue(promoCode.enabled());

        assertEquals(1, fixture.campaigns.stored.size());
        assertEquals(1, fixture.promoCodes.stored.size());
        assertEquals(List.of(PromotionAuditType.CAMPAIGN_CREATED, PromotionAuditType.PROMO_CODE_CREATED), fixture.audit.types());
        assertEquals(PromotionsTestFixture.ORGANIZER, fixture.audit.records.get(0).actorUserId());
        assertEquals(fixture.eventId, fixture.audit.records.get(0).eventId());
    }

    @Test
    void fixedDiscountIsCreatedInKzt() {
        CreateCampaignCommand command = new CreateCampaignCommand(
            fixture.eventId,
            "Early bird",
            DiscountType.FIXED_KZT,
            BigDecimal.valueOf(1_500),
            NOW,
            NOW.plusSeconds(86_400),
            null,
            null
        );

        CampaignView campaign = fixture.campaignService.create(command);

        assertEquals(DiscountType.FIXED_KZT, campaign.discountType());
        assertEquals(0, BigDecimal.valueOf(1_500).compareTo(campaign.discountValue()));
        assertTrue(campaign.applicableTicketTypeIds().isEmpty());
        assertNull(campaign.maxRedemptions());
        assertNull(campaign.remainingRedemptions());
    }

    @Test
    void userWhoCannotManageTheEventCannotCreateCampaigns() {
        fixture.currentUserId = STRANGER;

        assertThrows(AuthorizationException.class, () -> fixture.createTenPercentOnVip(10));
        assertTrue(fixture.campaigns.stored.isEmpty());
        assertTrue(fixture.audit.records.isEmpty());
    }

    @Test
    void creatingCampaignsRequiresAnAuthenticatedUser() {
        fixture.currentUserId = null;

        assertThrows(UnauthenticatedActorException.class, () -> fixture.createTenPercentOnVip(10));
    }

    @Test
    void campaignsCannotBeCreatedForCancelledEvents() {
        fixture.eventManagement.cancelledEvents.add(fixture.eventId);

        assertThrows(InvalidCampaignStateException.class, () -> fixture.createTenPercentOnVip(10));
        assertTrue(fixture.campaigns.stored.isEmpty());
    }

    @Test
    void applicableTicketTypesMustBelongToTheEvent() {
        CreateCampaignCommand command = new CreateCampaignCommand(
            fixture.eventId,
            "Student week",
            DiscountType.PERCENTAGE,
            BigDecimal.TEN,
            NOW,
            NOW.plusSeconds(86_400),
            Set.of(UUID.randomUUID()),
            null
        );

        assertThrows(IllegalArgumentException.class, () -> fixture.campaignService.create(command));
        assertTrue(fixture.campaigns.stored.isEmpty());
    }

    @Test
    void organizerEndsACampaign() {
        CampaignView created = fixture.createTenPercentOnVip(10);

        CampaignView ended = fixture.campaignService.end(new EndCampaignCommand(created.id()));

        assertEquals(CampaignStatus.ENDED, ended.status());
        assertEquals(NOW, ended.endedAt());
        assertEquals(1, ended.promoCodes().size());
        assertEquals(PromotionAuditType.CAMPAIGN_ENDED, fixture.audit.types().get(fixture.audit.types().size() - 1));
    }

    @Test
    void strangerCannotEndOrReadACampaign() {
        CampaignView created = fixture.createTenPercentOnVip(10);
        fixture.currentUserId = STRANGER;

        assertThrows(AuthorizationException.class, () -> fixture.campaignService.end(new EndCampaignCommand(created.id())));
        assertThrows(AuthorizationException.class, () -> fixture.campaignService.get(created.id()));
        assertThrows(AuthorizationException.class, () -> fixture.campaignService.listForEvent(fixture.eventId));
    }

    @Test
    void unknownCampaignIsNotFound() {
        assertThrows(EntityNotFoundException.class, () -> fixture.campaignService.get(UUID.randomUUID()));
    }

    @Test
    void listingReturnsTheEventsCampaigns() {
        fixture.createTenPercentOnVip(10);
        fixture.createTenPercentOnVip(20);

        List<CampaignView> campaigns = fixture.campaignService.listForEvent(fixture.eventId);

        assertEquals(2, campaigns.size());
        assertNotEquals(campaigns.get(0).promoCodes().get(0).code(), campaigns.get(1).promoCodes().get(0).code());
    }
}
