package com.biletflow.biletflow.promotions.domain.campaign;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import com.biletflow.biletflow.promotions.domain.exceptions.PromoCodeRejectedException;
import com.biletflow.biletflow.promotions.domain.exceptions.RejectionReason;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final Instant HOLD_EXPIRES = NOW.plusSeconds(600);
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
    void creatingACampaignMakesItActiveWithNoRedemptions() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.of(5));

        assertNotNull(campaign.getId());
        assertEquals("Student week", campaign.getName());
        assertEquals(CampaignStatus.ACTIVE, campaign.getStatus());
        assertEquals(NOW, campaign.getCreatedAt());
        assertTrue(campaign.getRedemptions().isEmpty());
        assertEquals(5, campaign.remainingRedemptionsAt(NOW));
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
        DiscountQuote quote = tenPercentOnVip(RedemptionLimit.unlimited()).quote(UUID.randomUUID(), vipAndStandard(), NOW);

        assertEquals(Money.kzt(20_000), quote.eligibleSubtotal());
        assertEquals(Money.kzt(2_000), quote.discountAmount());
    }

    @Test
    void emptyTicketTypeSetMeansEveryTicketTypeIsEligible() {
        Campaign campaign = campaign(Discount.fixedKzt(Money.kzt(1_000)), Set.of(), RedemptionLimit.unlimited());

        DiscountQuote quote = campaign.quote(UUID.randomUUID(), vipAndStandard(), NOW);

        assertEquals(Money.kzt(25_000), quote.eligibleSubtotal());
        assertEquals(Money.kzt(1_000), quote.discountAmount());
    }

    @Test
    void codeIsInapplicableForAnotherEvent() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());

        campaign.ensureAppliesToEvent(campaign.getEventId());
        assertEquals(RejectionReason.INAPPLICABLE, rejection(() -> campaign.ensureAppliesToEvent(UUID.randomUUID())));
    }

    @Test
    void codeIsInapplicableWhenNoSelectedTicketIsEligible() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        List<CheckoutLine> standardOnly = List.of(new CheckoutLine(standard, Money.kzt(5_000), 1));

        assertEquals(RejectionReason.INAPPLICABLE, rejection(() -> campaign.quote(UUID.randomUUID(), standardOnly, NOW)));
    }

    @Test
    void codeIsInapplicableForFreeTickets() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        List<CheckoutLine> freeVip = List.of(new CheckoutLine(vip, Money.zeroKzt(), 1));

        assertEquals(RejectionReason.INAPPLICABLE, rejection(() -> campaign.quote(UUID.randomUUID(), freeVip, NOW)));
    }

    @Test
    void codeIsRejectedOutsideTheValidityPeriod() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        UUID session = UUID.randomUUID();

        Instant beforeStart = VALIDITY.startsAt().minusSeconds(1);
        Instant atEnd = VALIDITY.endsAt();

        assertEquals(RejectionReason.NOT_YET_VALID, rejection(() -> campaign.quote(session, vipAndStandard(), beforeStart)));
        assertEquals(RejectionReason.EXPIRED, rejection(() -> campaign.quote(session, vipAndStandard(), atEnd)));
    }

    @Test
    void reservingStoresTheDiscountSnapshot() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.of(5));
        UUID session = UUID.randomUUID();

        PromoRedemption redemption = campaign.reserveRedemption(session, vipAndStandard(), NOW, HOLD_EXPIRES);

        assertEquals(RedemptionStatus.RESERVED, redemption.getStatus());
        assertEquals(session, redemption.getSessionId());
        assertEquals(Money.kzt(2_000), redemption.getDiscountAmount());
        assertEquals(HOLD_EXPIRES, redemption.getExpiresAt());
        assertEquals(4, campaign.remainingRedemptionsAt(NOW));
    }

    @Test
    void reservationsBeyondTheLimitAreRejected() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.of(1));
        campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES);

        assertEquals(
            RejectionReason.EXHAUSTED,
            rejection(() -> campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES))
        );
    }

    @Test
    void sameSessionReservingAgainReplacesItsPreviousReservation() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.of(1));
        UUID session = UUID.randomUUID();
        PromoRedemption first = campaign.reserveRedemption(session, vipAndStandard(), NOW, HOLD_EXPIRES);

        List<CheckoutLine> oneVip = List.of(new CheckoutLine(vip, Money.kzt(10_000), 1));
        PromoRedemption second = campaign.reserveRedemption(session, oneVip, NOW, HOLD_EXPIRES);

        assertEquals(RedemptionStatus.RELEASED, first.getStatus());
        assertEquals(RedemptionStatus.RESERVED, second.getStatus());
        assertEquals(Money.kzt(1_000), second.getDiscountAmount());
        assertEquals(1, campaign.usedRedemptionsAt(NOW));
    }

    @Test
    void releasedReservationFreesTheRedemption() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.of(1));
        UUID session = UUID.randomUUID();
        PromoRedemption redemption = campaign.reserveRedemption(session, vipAndStandard(), NOW, HOLD_EXPIRES);

        campaign.releaseRedemption(redemption.getId(), session);
        campaign.releaseRedemption(redemption.getId(), session);

        assertEquals(RedemptionStatus.RELEASED, redemption.getStatus());
        assertNotNull(campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES));
    }

    @Test
    void anotherSessionCannotReleaseAReservation() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        PromoRedemption redemption = campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES);

        assertThrows(InvalidCampaignStateException.class, () -> campaign.releaseRedemption(redemption.getId(), UUID.randomUUID()));
    }

    @Test
    void expiredReservationsStopCountingAgainstTheLimit() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.of(1));
        PromoRedemption stale = campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES);

        Instant later = HOLD_EXPIRES;
        PromoRedemption fresh = campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), later, later.plusSeconds(600));

        assertEquals(RedemptionStatus.EXPIRED, stale.getStatus());
        assertEquals(RedemptionStatus.RESERVED, fresh.getStatus());
    }

    @Test
    void confirmingLinksTheRedemptionToTheOrderAndRetriesAreIdempotent() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.of(1));
        PromoRedemption redemption = campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES);
        UUID order = UUID.randomUUID();

        campaign.confirmRedemption(redemption.getId(), order, NOW.plusSeconds(60));
        campaign.confirmRedemption(redemption.getId(), order, NOW.plusSeconds(120));

        assertEquals(RedemptionStatus.CONFIRMED, redemption.getStatus());
        assertEquals(order, redemption.getOrderId());
        assertEquals(NOW.plusSeconds(60), redemption.getConfirmedAt());
        assertEquals(1, campaign.confirmedRedemptionCount());
        assertEquals(0, campaign.remainingRedemptionsAt(HOLD_EXPIRES.plusSeconds(1)));
    }

    @Test
    void confirmedRedemptionCannotBeConfirmedForAnotherOrder() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        PromoRedemption redemption = campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES);
        campaign.confirmRedemption(redemption.getId(), UUID.randomUUID(), NOW);

        assertThrows(InvalidCampaignStateException.class, () -> campaign.confirmRedemption(redemption.getId(), UUID.randomUUID(), NOW));
    }

    @Test
    void expiredReservationCannotBeConfirmed() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        PromoRedemption redemption = campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES);

        UUID order = UUID.randomUUID();
        assertThrows(InvalidCampaignStateException.class, () -> campaign.confirmRedemption(redemption.getId(), order, HOLD_EXPIRES));
    }

    @Test
    void endedCampaignRejectsNewReservationsButStillConfirmsExistingOnes() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());
        PromoRedemption redemption = campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES);

        campaign.end(NOW);

        assertEquals(CampaignStatus.ENDED, campaign.getStatus());
        assertEquals(
            RejectionReason.CAMPAIGN_ENDED,
            rejection(() -> campaign.reserveRedemption(UUID.randomUUID(), vipAndStandard(), NOW, HOLD_EXPIRES))
        );
        campaign.confirmRedemption(redemption.getId(), UUID.randomUUID(), NOW.plusSeconds(1));
        assertEquals(RedemptionStatus.CONFIRMED, redemption.getStatus());
        assertThrows(InvalidCampaignStateException.class, () -> campaign.end(NOW));
    }

    @Test
    void redemptionListCannotBeModifiedFromOutside() {
        Campaign campaign = tenPercentOnVip(RedemptionLimit.unlimited());

        assertThrows(UnsupportedOperationException.class, () -> campaign.getRedemptions().clear());
    }
}
