package com.biletflow.biletflow.promotions.application;

import static com.biletflow.biletflow.promotions.application.PromotionsTestFixture.LIFETIME;
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
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionSource;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedemptionServiceTest {

    private final PromotionsTestFixture fixture = new PromotionsTestFixture();
    private final UUID checkoutId = UUID.randomUUID();

    private List<PromotionLine> vipAndStandard() {
        return List.of(new PromotionLine(fixture.vip, Money.kzt(10_000), 2), new PromotionLine(fixture.standard, Money.kzt(5_000), 1));
    }

    private String createCode(Integer maxRedemptions) {
        return fixture.createTenPercentOnVip(maxRedemptions).promoCodes().get(0).code();
    }

    private ReservedPromotionView reserve(String code, UUID checkout) {
        ReservePromotionCommand command = new ReservePromotionCommand(
            fixture.eventId,
            checkout,
            code,
            vipAndStandard(),
            RedemptionSource.CODE
        );
        return fixture.redemptionService.reserve(command);
    }

    private PromoRedemption stored(ReservedPromotionView reserved) {
        return fixture.redemptions.stored.get(new RedemptionId(reserved.redemptionId()));
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
        assertTrue(fixture.redemptions.stored.isEmpty());
    }

    @Test
    void checkoutDeadlineOverridesStandaloneLifetimeAndAcceptsOnTimeFulfillment() {
        String code = createCode(1);
        var deadline = NOW.plusSeconds(90);
        var reserved = fixture.redemptionService.reserve(
            new ReservePromotionCommand(fixture.eventId, checkoutId, code, vipAndStandard(), RedemptionSource.CODE, deadline)
        );
        assertEquals(deadline, reserved.expiresAt());

        UUID orderId = UUID.randomUUID();
        fixture.redemptionService.confirm(new ConfirmRedemptionCommand(reserved.redemptionId(), orderId, deadline.minusNanos(1)));
        assertEquals(orderId, stored(reserved).getOrderId());
        assertEquals(RedemptionStatus.CONFIRMED, stored(reserved).getStatus());
    }

    @Test
    void quoteIgnoresTheSessionsOwnReservation() {
        String code = createCode(1);
        reserve(code, checkoutId);

        QuotePromotionCommand command = new QuotePromotionCommand(fixture.eventId, checkoutId, code, vipAndStandard());

        PromotionQuoteView quote = fixture.redemptionService.quote(command);

        assertEquals(Money.kzt(2_000), quote.discountAmount());
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
    void reservationLocksTheCampaignAndRecordsAttribution() {
        String code = createCode(5);
        ReservePromotionCommand command = new ReservePromotionCommand(
            fixture.eventId,
            checkoutId,
            code,
            vipAndStandard(),
            RedemptionSource.CAMPAIGN_LINK
        );

        ReservedPromotionView reserved = fixture.redemptionService.reserve(command);

        assertEquals(1, fixture.campaigns.lockedLoads);
        assertEquals(Money.kzt(2_000), reserved.discountAmount());
        PromoRedemption redemption = stored(reserved);
        assertEquals(RedemptionSource.CAMPAIGN_LINK, redemption.getSource());
        assertEquals(2, redemption.getTicketQuantity());
        assertEquals(Money.kzt(20_000), redemption.getGrossAmount());
        assertEquals(4, campaign().remainingRedemptions());
    }

    @Test
    void promotionsDecidesTheReservationLifetime() {
        ReservedPromotionView reserved = reserve(createCode(null), checkoutId);

        assertEquals(NOW.plus(LIFETIME), reserved.expiresAt());
    }

    @Test
    void confirmingAttributesTheSaleToTheCampaign() {
        String code = createCode(5);
        ReservedPromotionView reserved = reserve(code, checkoutId);
        UUID order = UUID.randomUUID();

        fixture.redemptionService.confirm(new ConfirmRedemptionCommand(reserved.redemptionId(), order));
        fixture.redemptionService.confirm(new ConfirmRedemptionCommand(reserved.redemptionId(), order));

        assertEquals(order, stored(reserved).getOrderId());
        CampaignView campaign = campaign();
        assertEquals(1, campaign.confirmedRedemptions());
        assertEquals(2, campaign.ticketsSold());
        assertEquals(Money.kzt(20_000), campaign.grossRevenue());
        assertEquals(Money.kzt(2_000), campaign.totalDiscountGiven());
        assertEquals(Money.kzt(18_000), campaign.netRevenue());
    }

    @Test
    void lastRedemptionCanOnlyBeReservedOnce() {
        String code = createCode(1);
        reserve(code, checkoutId);

        assertEquals(RejectionReason.EXHAUSTED, rejection(() -> reserve(code, UUID.randomUUID())));
    }

    @Test
    void reservingAgainForTheSameCheckoutReplacesThePreviousReservation() {
        String code = createCode(1);
        ReservedPromotionView first = reserve(code, checkoutId);

        ReservedPromotionView second = reserve(code, checkoutId);

        assertEquals(RedemptionStatus.RELEASED, stored(first).getStatus());
        assertEquals(RedemptionStatus.RESERVED, stored(second).getStatus());
        assertEquals(0, campaign().remainingRedemptions());
    }

    @Test
    void expiredReservationsFreeTheirRedemption() {
        String code = createCode(1);
        ReservedPromotionView stale = reserve(code, checkoutId);

        fixture.clock.advance(LIFETIME);

        assertNotNull(reserve(code, UUID.randomUUID()));
        assertThrows(RuntimeException.class, () ->
            fixture.redemptionService.confirm(new ConfirmRedemptionCommand(stale.redemptionId(), UUID.randomUUID()))
        );
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

    @Test
    void reservationAfterTheLifetimeStillRespectsOtherActiveReservations() {
        String code = createCode(2);
        reserve(code, UUID.randomUUID());
        fixture.clock.advance(Duration.ofMinutes(5));
        reserve(code, UUID.randomUUID());

        assertEquals(RejectionReason.EXHAUSTED, rejection(() -> reserve(code, UUID.randomUUID())));
    }
}
