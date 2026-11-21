package com.biletflow.biletflow.promotions.domain.redemption;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.DiscountQuote;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PromoRedemption {

    private final RedemptionId id;
    private final CampaignId campaignId;
    private final PromoCodeId promoCodeId;
    private final UUID sessionId;
    private final RedemptionSource source;
    private final int ticketQuantity;
    private final Money grossAmount;
    private final Money discountAmount;
    private final Instant reservedAt;
    private final Instant expiresAt;

    private UUID orderId;
    private RedemptionStatus status;
    private Instant confirmedAt;

    private PromoRedemption(
        RedemptionId id,
        CampaignId campaignId,
        PromoCodeId promoCodeId,
        UUID sessionId,
        RedemptionSource source,
        int ticketQuantity,
        Money grossAmount,
        Money discountAmount,
        Instant reservedAt,
        Instant expiresAt,
        UUID orderId,
        RedemptionStatus status,
        Instant confirmedAt
    ) {
        this.id = Objects.requireNonNull(id, "RedemptionId cannot be null");
        this.campaignId = Objects.requireNonNull(campaignId, "CampaignId cannot be null");
        this.promoCodeId = Objects.requireNonNull(promoCodeId, "PromoCodeId cannot be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.grossAmount = Objects.requireNonNull(grossAmount, "grossAmount cannot be null");
        this.discountAmount = Objects.requireNonNull(discountAmount, "discountAmount cannot be null");
        this.reservedAt = Objects.requireNonNull(reservedAt, "reservedAt cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        this.orderId = orderId;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.confirmedAt = confirmedAt;

        if (ticketQuantity <= 0) {
            throw new IllegalArgumentException("Ticket quantity must be greater than 0");
        }
        this.ticketQuantity = ticketQuantity;

        if (discountAmount.isGreaterThan(grossAmount)) {
            throw new IllegalArgumentException("Discount cannot exceed the gross amount");
        }

        if (!expiresAt.isAfter(reservedAt)) {
            throw new IllegalArgumentException("Redemption must expire after it is reserved");
        }

        if (status == RedemptionStatus.CONFIRMED && (orderId == null || confirmedAt == null)) {
            throw new IllegalStateException("Confirmed redemption must have an orderId and confirmedAt");
        }
    }

    public static PromoRedemption reserve(
        CampaignId campaignId,
        PromoCodeId promoCodeId,
        UUID sessionId,
        RedemptionSource source,
        DiscountQuote quote,
        Instant now,
        ReservationLifetime lifetime
    ) {
        Objects.requireNonNull(quote, "quote cannot be null");
        Objects.requireNonNull(lifetime, "lifetime cannot be null");

        return new PromoRedemption(
            RedemptionId.generate(),
            campaignId,
            promoCodeId,
            sessionId,
            source,
            quote.eligibleTicketQuantity(),
            quote.eligibleSubtotal(),
            quote.discountAmount(),
            now,
            lifetime.expiresAtFrom(now),
            null,
            RedemptionStatus.RESERVED,
            null
        );
    }

    public static PromoRedemption rehydrate(
        RedemptionId id,
        CampaignId campaignId,
        PromoCodeId promoCodeId,
        UUID sessionId,
        RedemptionSource source,
        int ticketQuantity,
        Money grossAmount,
        Money discountAmount,
        Instant reservedAt,
        Instant expiresAt,
        UUID orderId,
        RedemptionStatus status,
        Instant confirmedAt
    ) {
        return new PromoRedemption(
            id,
            campaignId,
            promoCodeId,
            sessionId,
            source,
            ticketQuantity,
            grossAmount,
            discountAmount,
            reservedAt,
            expiresAt,
            orderId,
            status,
            confirmedAt
        );
    }

    public boolean confirm(UUID orderId, Instant now) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        if (status == RedemptionStatus.CONFIRMED) {
            if (orderId.equals(this.orderId)) {
                return false;
            }
            throw new InvalidCampaignStateException("Redemption is already confirmed for another order");
        }

        ensureReserved();

        if (isExpiredAt(now)) {
            throw new InvalidCampaignStateException("Redemption reservation has expired");
        }

        this.orderId = orderId;
        this.confirmedAt = now;
        this.status = RedemptionStatus.CONFIRMED;
        return true;
    }

    public void release(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");

        if (!this.sessionId.equals(sessionId)) {
            throw new InvalidCampaignStateException("Redemption belongs to another checkout session");
        }

        if (status == RedemptionStatus.RELEASED || status == RedemptionStatus.EXPIRED) {
            return;
        }

        ensureReserved();
        this.status = RedemptionStatus.RELEASED;
    }

    public void markExpired() {
        ensureReserved();
        this.status = RedemptionStatus.EXPIRED;
    }

    public boolean isExpiredAt(Instant now) {
        return !Objects.requireNonNull(now, "now cannot be null").isBefore(expiresAt);
    }

    public boolean countsTowardsLimitAt(Instant now) {
        return status == RedemptionStatus.CONFIRMED || (status == RedemptionStatus.RESERVED && !isExpiredAt(now));
    }

    public Money netAmount() {
        return grossAmount.subtract(discountAmount);
    }

    private void ensureReserved() {
        if (status != RedemptionStatus.RESERVED) {
            throw new InvalidCampaignStateException("Redemption is not reserved: " + status);
        }
    }

    public RedemptionId getId() {
        return id;
    }

    public CampaignId getCampaignId() {
        return campaignId;
    }

    public PromoCodeId getPromoCodeId() {
        return promoCodeId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public RedemptionSource getSource() {
        return source;
    }

    public int getTicketQuantity() {
        return ticketQuantity;
    }

    public Money getGrossAmount() {
        return grossAmount;
    }

    public Money getDiscountAmount() {
        return discountAmount;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public RedemptionStatus getStatus() {
        return status;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
