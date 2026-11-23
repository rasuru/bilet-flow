package com.biletflow.biletflow.promotions.domain.campaign;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import com.biletflow.biletflow.promotions.domain.exceptions.PromoCodeRejectedException;
import com.biletflow.biletflow.promotions.domain.exceptions.RejectionReason;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final ValidityPeriod VALIDITY = new ValidityPeriod(NOW.minusSeconds(3_600), NOW.plusSeconds(86_400));

    private final UUID vip = UUID.randomUUID();
    private final UUID standard = UUID.randomUUID();

    private Campaign campaign(Discount discount, Set<UUID> ticketTypes, RedemptionLimit limit) {
        return Campaign.create(UUID.randomUUID(), "Student week", discount, VALIDITY, ticketTypes, limit, NOW);
    }

    private Campaign tenPercentOnVip(RedemptionLimit limit) {
        return campaign(Discount.percentage(10), Set.of(vip), limit);
    }

    private List<CheckoutLine> vipAndStandard() {
        return List.of(new CheckoutLine(vip, Money.kzt(10_000), 2), new CheckoutLine(standard, Money.kzt(5_000), 1));
    }

    private static RejectionReason rejection(Runnable action) {
        return assertThrows(PromoCodeRejectedException.class, action::run).getReason();
    }

    @Test
    void creatingACampaignMakesItActive() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.of(5));

        assertNotNull(campaign.getId());
        assertEquals("Student week", campaign.getName());
        assertEquals(CampaignStatus.ACTIVE, campaign.getStatus());
        assertEquals(NOW, campaign.getCreatedAt());
        assertEquals(5, campaign.remainingRedemptions(0));
    }

    @Test
    void campaignCannotBeCreatedWithAValidityPeriodThatAlreadyEnded() {
        ValidityPeriod past = new ValidityPeriod(NOW.minusSeconds(7_200), NOW.minusSeconds(3_600));

        assertThrows(IllegalArgumentException.class, () ->
            Campaign.create(UUID.randomUUID(), "Old", Discount.percentage(10), past, Set.of(), RedemptionLimit.unlimited(), NOW)
        );
    }

    @Test
    void campaignNameIsRequired() {
        assertThrows(IllegalArgumentException.class, () ->
            Campaign.create(UUID.randomUUID(), "  ", Discount.percentage(10), VALIDITY, Set.of(), RedemptionLimit.unlimited(), NOW)
        );
    }

    @Test
    void discountAppliesOnlyToApplicableTicketTypes() {
        DiscountQuote quote = tenPercentOnVip(RedemptionLimit.unlimited()).quote(vipAndStandard(), 0, NOW);

        assertEquals(Money.kzt(20_000), quote.eligibleSubtotal());
        assertEquals(2, quote.eligibleTicketQuantity());
        assertEquals(Money.kzt(2_000), quote.discountAmount());
    }

    @Test
    void emptyTicketTypeSetMeansEveryTicketTypeIsEligible() {
        Campaign campaign = campaign(Discount.fixedKzt(Money.kzt(1_000)), Set.of(), RedemptionLimit.unlimited());

        DiscountQuote quote = campaign.quote(vipAndStandard(), 0, NOW);

        assertEquals(Money.kzt(25_000), quote.eligibleSubtotal());
        assertEquals(3, quote.eligibleTicketQuantity());
        assertEquals(Money.kzt(1_000), quote.discountAmount());
    }

    @Test
    void codeIsInapplicableWhenNoSelectedTicketIsEligible() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        List<CheckoutLine> standardOnly = List.of(new CheckoutLine(standard, Money.kzt(5_000), 1));

        assertEquals(RejectionReason.INAPPLICABLE, rejection(() -> campaign.quote(standardOnly, 0, NOW)));
    }

    @Test
    void codeIsInapplicableForFreeTickets() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        List<CheckoutLine> freeVip = List.of(new CheckoutLine(vip, Money.zeroKzt(), 1));

        assertEquals(RejectionReason.INAPPLICABLE, rejection(() -> campaign.quote(freeVip, 0, NOW)));
    }

    @Test
    void codeIsInapplicableForAnotherEvent() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());

        campaign.ensureAppliesToEvent(campaign.getEventId());
        assertEquals(RejectionReason.INAPPLICABLE, rejection(() -> campaign.ensureAppliesToEvent(UUID.randomUUID())));
    }

    @Test
    void codeIsRejectedOutsideTheValidityPeriod() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        Instant beforeStart = VALIDITY.startsAt().minusSeconds(1);
        Instant atEnd = VALIDITY.endsAt();

        assertEquals(RejectionReason.NOT_YET_VALID, rejection(() -> campaign.quote(vipAndStandard(), 0, beforeStart)));
        assertEquals(RejectionReason.EXPIRED, rejection(() -> campaign.quote(vipAndStandard(), 0, atEnd)));
    }

    @Test
    void codeIsRejectedOnceTheLimitIsReached() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.of(2));

        campaign.ensureAcceptsRedemptions(1, NOW);
        assertEquals(RejectionReason.EXHAUSTED, rejection(() -> campaign.quote(vipAndStandard(), 2, NOW)));
        assertEquals(0, campaign.remainingRedemptions(2));
    }

    @Test
    void unlimitedCampaignHasNoRemainingCount() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());

        campaign.ensureAcceptsRedemptions(1_000_000, NOW);
        assertNull(campaign.remainingRedemptions(5));
    }

    @Test
    void endedCampaignRejectsRedemptionsAndCannotEndTwice() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());

        campaign.end(NOW);

        assertEquals(CampaignStatus.ENDED, campaign.getStatus());
        assertEquals(NOW, campaign.getEndedAt());
        assertEquals(RejectionReason.CAMPAIGN_ENDED, rejection(() -> campaign.quote(vipAndStandard(), 0, NOW)));
        assertThrows(InvalidCampaignStateException.class, () -> campaign.end(NOW));
    }
}
