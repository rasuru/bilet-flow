package com.biletflow.biletflow.promotions.ohs;

import static com.biletflow.biletflow.promotions.application.PromotionsTestFixture.NOW;
import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.application.PromotionsTestFixture;
import com.biletflow.biletflow.promotions.application.campaign.view.CampaignView;
import com.biletflow.biletflow.promotions.application.promocode.command.DisablePromoCodeCommand;
import com.biletflow.biletflow.promotions.domain.exceptions.RejectionReason;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionSource;
import com.biletflow.biletflow.promotions.ohs.PromotionRejectedException.Reason;
import com.biletflow.biletflow.promotions.ohs.PromotionsOhs.Line;
import com.biletflow.biletflow.promotions.ohs.PromotionsOhs.Quote;
import com.biletflow.biletflow.promotions.ohs.PromotionsOhs.Reservation;
import com.biletflow.biletflow.promotions.ohs.PromotionsOhs.Source;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultPromotionsOhsTest {

    private final PromotionsTestFixture fixture = new PromotionsTestFixture();
    private final PromotionsOhs ohs = new DefaultPromotionsOhs(fixture.redemptionService);
    private final UUID checkoutId = UUID.randomUUID();

    private List<Line> twoVip() {
        return List.of(new Line(fixture.vip, Money.kzt(10_000), 2));
    }

    private CampaignView campaign() {
        return fixture.campaignService.listForEvent(fixture.eventId).get(0);
    }

    private static Reason rejection(Runnable action) {
        return assertThrows(PromotionRejectedException.class, action::run).getReason();
    }

    @Test
    void quoteReturnsTheDiscountWithoutUsingARedemption() {
        String code = fixture.createTenPercentOnVip(1).promoCodes().get(0).code();

        Quote quote = ohs.quote(fixture.eventId, checkoutId, code, twoVip());

        assertEquals(code, quote.code());
        assertEquals(Money.kzt(20_000), quote.eligibleSubtotal());
        assertEquals(Money.kzt(2_000), quote.discountAmount());
        assertEquals(1, campaign().remainingRedemptions());
    }

    @Test
    void reserveConfirmFlowAttributesTheOrderToTheCampaign() {
        String code = fixture.createTenPercentOnVip(5).promoCodes().get(0).code();

        Reservation reservation = ohs.reserve(fixture.eventId, checkoutId, code, twoVip(), Source.CODE);

        assertEquals(Money.kzt(2_000), reservation.discountAmount());
        assertEquals(NOW.plus(PromotionsTestFixture.LIFETIME), reservation.expiresAt());
        assertEquals(campaign().id(), reservation.campaignId());

        ohs.confirm(reservation.redemptionId(), UUID.randomUUID());

        assertEquals(1, campaign().confirmedRedemptions());
        assertEquals(Money.kzt(2_000), campaign().totalDiscountGiven());
    }

    @Test
    void releaseGivesTheRedemptionBack() {
        String code = fixture.createTenPercentOnVip(1).promoCodes().get(0).code();
        Reservation reservation = ohs.reserve(fixture.eventId, checkoutId, code, twoVip(), Source.CODE);

        ohs.release(reservation.redemptionId(), checkoutId);

        assertEquals(1, campaign().remainingRedemptions());
    }

    @Test
    void rejectionsAreReportedInTheOhsLanguage() {
        CampaignView created = fixture.createTenPercentOnVip(1);
        String code = created.promoCodes().get(0).code();

        assertEquals(Reason.NOT_FOUND, rejection(() -> ohs.quote(fixture.eventId, checkoutId, "NOSUCHCODE", twoVip())));
        assertEquals(Reason.INAPPLICABLE, rejection(() -> ohs.quote(UUID.randomUUID(), checkoutId, code, twoVip())));

        ohs.reserve(fixture.eventId, checkoutId, code, twoVip(), Source.CODE);
        assertEquals(Reason.EXHAUSTED, rejection(() -> ohs.reserve(fixture.eventId, UUID.randomUUID(), code, twoVip(), Source.CODE)));

        fixture.promoCodeService.disable(new DisablePromoCodeCommand(created.promoCodes().get(0).id()));
        assertEquals(Reason.DISABLED, rejection(() -> ohs.quote(fixture.eventId, checkoutId, code, twoVip())));
    }

    @Test
    void everyDomainRejectionReasonHasAnOhsReason() {
        for (RejectionReason reason : RejectionReason.values()) {
            assertEquals(reason.name(), Reason.valueOf(reason.name()).name());
        }
    }

    @Test
    void everyOhsSourceHasADomainSource() {
        for (Source source : Source.values()) {
            assertEquals(source.name(), RedemptionSource.valueOf(source.name()).name());
        }
    }

    @Test
    void confirmingAnUnknownRedemptionIsNotFound() {
        assertThrows(EntityNotFoundException.class, () -> ohs.confirm(UUID.randomUUID(), UUID.randomUUID()));
    }
}
