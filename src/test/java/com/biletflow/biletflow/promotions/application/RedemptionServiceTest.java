package com.biletflow.biletflow.promotions.application;

import static com.biletflow.biletflow.promotions.application.PromotionsTestFixture.NOW;
import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.application.campaign.view.CampaignView;
import com.biletflow.biletflow.promotions.application.promocode.command.DisablePromoCodeCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.ConfirmRedemptionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.PromotionLine;
import com.biletflow.biletflow.promotions.application.redemption.command.QuotePromotionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.ReleaseRedemptionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.ReservePromotionCommand;
import com.biletflow.biletflow.promotions.application.redemption.view.PromotionQuoteView;
import com.biletflow.biletflow.promotions.application.redemption.view.ReservedPromotionView;
import com.biletflow.biletflow.promotions.domain.exceptions.PromoCodeRejectedException;
import com.biletflow.biletflow.promotions.domain.exceptions.RejectionReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedemptionServiceTest {

    private static final Instant EXPIRES_AT = NOW.plusSeconds(600);

    private final PromotionsTestFixture fixture = new PromotionsTestFixture();
    private final UUID checkoutId = UUID.randomUUID();

    private List<PromotionLine> vipAndStandard() {
        return List.of(new PromotionLine(fixture.vip, Money.kzt(10_000), 2), new PromotionLine(fixture.standard, Money.kzt(5_000), 1));
    }

    private String createCode(Integer maxRedemptions) {
        CampaignView campaign = fixture.createTenPercentOnVip(maxRedemptions);
        return campaign.promoCodes().get(0).code();
    }

    private ReservedPromotionView reserve(String code, UUID checkout) {
        ReservePromotionCommand command = new ReservePromotionCommand(fixture.eventId, checkout, code, vipAndStandard(), EXPIRES_AT);
        return fixture.redemptionService.reserve(command);
    }

    private CampaignView campaign() {
        return fixture.campaignService.listForEvent(fixture.eventId).get(0);
    }

    private static RejectionReason rejection(Runnable action) {
        return assertThrows(PromoCodeRejectedException.class, action::run).getReason();
    }

    @Test
    void quoteCalculatesTheDiscountWithoutReservingIt() {
        String code = createCode(1);

        QuotePromotionCommand command = new QuotePromotionCommand(fixture.eventId, checkoutId, code, vipAndStandard());

        PromotionQuoteView quote = fixture.redemptionService.quote(command);

        assertEquals(code, quote.code());
        assertEquals(Money.kzt(20_000), quote.eligibleSubtotal());
        assertEquals(Money.kzt(2_000), quote.discountAmount());
        assertEquals(1, campaign().remainingRedemptions());
    }

    @Test
    void codeIsMatchedCaseInsensitively() {
        String code = createCode(null);

        PromotionQuoteView quote = fixture.redemptionService.quote(
            new QuotePromotionCommand(fixture.eventId, checkoutId, code.toLowerCase(), vipAndStandard())
        );

        assertEquals(code, quote.code());
    }

    @Test
    void reservingAndConfirmingAttributesTheRedemptionToTheOrder() {
        String code = createCode(5);

        ReservedPromotionView reserved = reserve(code, checkoutId);

        assertEquals(Money.kzt(2_000), reserved.discountAmount());
        assertEquals(EXPIRES_AT, reserved.expiresAt());
        assertEquals(4, campaign().remainingRedemptions());

        fixture.redemptionService.confirm(new ConfirmRedemptionCommand(reserved.redemptionId(), UUID.randomUUID()));

        CampaignView campaign = campaign();
        assertEquals(1, campaign.confirmedRedemptions());
        assertEquals(Money.kzt(2_000), campaign.totalDiscountGiven());
    }

    @Test
    void lastRedemptionCanOnlyBeReservedOnce() {
        String code = createCode(1);
        reserve(code, checkoutId);

        assertEquals(RejectionReason.EXHAUSTED, rejection(() -> reserve(code, UUID.randomUUID())));
    }

    @Test
    void releasingGivesTheRedemptionBack() {
        String code = createCode(1);
        ReservedPromotionView reserved = reserve(code, checkoutId);

        fixture.redemptionService.release(new ReleaseRedemptionCommand(reserved.redemptionId(), checkoutId));

        assertEquals(1, campaign().remainingRedemptions());
        assertNotNull(reserve(code, UUID.randomUUID()));
    }

    @Test
    void unknownOrMalformedCodesAreNotFound() {
        createCode(null);

        assertThrows(EntityNotFoundException.class, () ->
            fixture.redemptionService.quote(new QuotePromotionCommand(fixture.eventId, checkoutId, "NOSUCHCODE", vipAndStandard()))
        );
        assertThrows(EntityNotFoundException.class, () ->
            fixture.redemptionService.quote(new QuotePromotionCommand(fixture.eventId, checkoutId, "bad code!", vipAndStandard()))
        );
    }

    @Test
    void disabledCodeIsRejected() {
        CampaignView campaign = fixture.createTenPercentOnVip(null);
        fixture.promoCodeService.disable(new DisablePromoCodeCommand(campaign.promoCodes().get(0).id()));
        String code = campaign.promoCodes().get(0).code();

        assertEquals(RejectionReason.DISABLED, rejection(() -> reserve(code, checkoutId)));
    }

    @Test
    void codeCannotBeUsedForAnotherEvent() {
        String code = createCode(null);
        QuotePromotionCommand otherEvent = new QuotePromotionCommand(UUID.randomUUID(), checkoutId, code, vipAndStandard());

        assertEquals(RejectionReason.INAPPLICABLE, rejection(() -> fixture.redemptionService.quote(otherEvent)));
    }

    @Test
    void confirmingAnUnknownRedemptionIsNotFound() {
        assertThrows(EntityNotFoundException.class, () ->
            fixture.redemptionService.confirm(new ConfirmRedemptionCommand(UUID.randomUUID(), UUID.randomUUID()))
        );
    }
}
