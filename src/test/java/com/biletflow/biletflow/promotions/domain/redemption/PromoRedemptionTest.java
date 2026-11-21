package com.biletflow.biletflow.promotions.domain.redemption;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.DiscountQuote;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromoRedemptionTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final ReservationLifetime LIFETIME = new ReservationLifetime(Duration.ofMinutes(15));
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(15));

    private final UUID session = UUID.randomUUID();

    private PromoRedemption reserved() {
        DiscountQuote quote = new DiscountQuote(Money.kzt(20_000), 2, Money.kzt(2_000));
        return PromoRedemption.reserve(
            CampaignId.generate(),
            PromoCodeId.generate(),
            session,
            RedemptionSource.CAMPAIGN_LINK,
            quote,
            NOW,
            LIFETIME
        );
    }

    @Test
    void reservationStoresAttributionAndUsesItsOwnLifetime() {
        PromoRedemption redemption = reserved();

        assertEquals(RedemptionStatus.RESERVED, redemption.getStatus());
        assertEquals(RedemptionSource.CAMPAIGN_LINK, redemption.getSource());
        assertEquals(2, redemption.getTicketQuantity());
        assertEquals(Money.kzt(20_000), redemption.getGrossAmount());
        assertEquals(Money.kzt(2_000), redemption.getDiscountAmount());
        assertEquals(Money.kzt(18_000), redemption.netAmount());
        assertEquals(NOW, redemption.getReservedAt());
        assertEquals(EXPIRES_AT, redemption.getExpiresAt());
        assertTrue(redemption.countsTowardsLimitAt(NOW));
    }

    @Test
    void lifetimeMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ReservationLifetime(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ReservationLifetime(Duration.ofMinutes(-1)));
        assertEquals(Duration.ofMinutes(15), ReservationLifetime.defaultLifetime().duration());
    }

    @Test
    void confirmingLinksTheOrderAndRetriesAreIdempotent() {
        PromoRedemption redemption = reserved();
        UUID order = UUID.randomUUID();

        assertTrue(redemption.confirm(order, NOW.plusSeconds(60)));
        assertFalse(redemption.confirm(order, NOW.plusSeconds(120)));

        assertEquals(RedemptionStatus.CONFIRMED, redemption.getStatus());
        assertEquals(order, redemption.getOrderId());
        assertEquals(NOW.plusSeconds(60), redemption.getConfirmedAt());
        assertTrue(redemption.countsTowardsLimitAt(EXPIRES_AT.plusSeconds(3_600)));
    }

    @Test
    void confirmedRedemptionCannotBeConfirmedForAnotherOrder() {
        PromoRedemption redemption = reserved();
        redemption.confirm(UUID.randomUUID(), NOW);

        assertThrows(InvalidCampaignStateException.class, () -> redemption.confirm(UUID.randomUUID(), NOW));
    }

    @Test
    void expiredReservationCannotBeConfirmedAndStopsCounting() {
        PromoRedemption redemption = reserved();

        assertFalse(redemption.countsTowardsLimitAt(EXPIRES_AT));
        assertThrows(InvalidCampaignStateException.class, () -> redemption.confirm(UUID.randomUUID(), EXPIRES_AT));
    }

    @Test
    void releaseIsIdempotentAndOnlyAllowedForTheOwningSession() {
        PromoRedemption redemption = reserved();

        assertThrows(InvalidCampaignStateException.class, () -> redemption.release(UUID.randomUUID()));

        redemption.release(session);
        redemption.release(session);

        assertEquals(RedemptionStatus.RELEASED, redemption.getStatus());
        assertFalse(redemption.countsTowardsLimitAt(NOW));
    }

    @Test
    void confirmedRedemptionCannotBeReleased() {
        PromoRedemption redemption = reserved();
        redemption.confirm(UUID.randomUUID(), NOW);

        assertThrows(InvalidCampaignStateException.class, () -> redemption.release(session));
    }

    @Test
    void discountCannotExceedTheGrossAmount() {
        assertThrows(IllegalArgumentException.class, () -> new DiscountQuote(Money.kzt(1_000), 1, Money.kzt(2_000)));
        assertThrows(IllegalArgumentException.class, () -> new DiscountQuote(Money.kzt(1_000), 0, Money.kzt(100)));
    }
}
