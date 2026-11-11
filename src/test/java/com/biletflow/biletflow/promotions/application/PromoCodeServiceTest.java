package com.biletflow.biletflow.promotions.application;

import static com.biletflow.biletflow.promotions.application.PromotionsTestFixture.NOW;
import static com.biletflow.biletflow.promotions.application.PromotionsTestFixture.STRANGER;
import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditType;
import com.biletflow.biletflow.promotions.application.campaign.view.CampaignView;
import com.biletflow.biletflow.promotions.application.promocode.command.DisablePromoCodeCommand;
import com.biletflow.biletflow.promotions.application.promocode.command.EnablePromoCodeCommand;
import com.biletflow.biletflow.promotions.application.promocode.view.CampaignLinkView;
import com.biletflow.biletflow.promotions.application.promocode.view.PromoCodeView;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromoCodeServiceTest {

    private final PromotionsTestFixture fixture = new PromotionsTestFixture();

    private PromoCodeView createdCode() {
        CampaignView campaign = fixture.createTenPercentOnVip(10);
        return campaign.promoCodes().get(0);
    }

    @Test
    void organizerDisablesAndEnablesAPromoCode() {
        PromoCodeView promoCode = createdCode();

        PromoCodeView disabled = fixture.promoCodeService.disable(new DisablePromoCodeCommand(promoCode.id()));

        assertFalse(disabled.enabled());
        assertEquals(NOW, disabled.disabledAt());

        PromoCodeView enabled = fixture.promoCodeService.enable(new EnablePromoCodeCommand(promoCode.id()));

        assertTrue(enabled.enabled());
        assertNull(enabled.disabledAt());
        assertTrue(fixture.audit.types().contains(PromotionAuditType.PROMO_CODE_DISABLED));
        assertTrue(fixture.audit.types().contains(PromotionAuditType.PROMO_CODE_ENABLED));
    }

    @Test
    void strangerCannotDisableAPromoCode() {
        PromoCodeView promoCode = createdCode();
        fixture.currentUserId = STRANGER;

        assertThrows(AuthorizationException.class, () -> fixture.promoCodeService.disable(new DisablePromoCodeCommand(promoCode.id())));
        assertTrue(fixture.promoCodes.stored.values().iterator().next().isEnabled());
    }

    @Test
    void unknownPromoCodeIsNotFound() {
        assertThrows(EntityNotFoundException.class, () -> fixture.promoCodeService.disable(new DisablePromoCodeCommand(UUID.randomUUID())));
    }

    @Test
    void campaignLinkResolvesToTheEventAndCode() {
        PromoCodeView promoCode = createdCode();
        fixture.currentUserId = null;

        CampaignLinkView link = fixture.promoCodeService.resolveCampaignLink(promoCode.campaignToken());

        assertEquals(fixture.eventId, link.eventId());
        assertEquals(promoCode.code(), link.code());
    }

    @Test
    void unknownCampaignLinkIsNotFound() {
        assertThrows(EntityNotFoundException.class, () -> fixture.promoCodeService.resolveCampaignLink(UUID.randomUUID()));
    }
}
