package com.biletflow.biletflow.promotions.domain.redemption;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PromoRedemption {
    private final RedemptionId id;
    private final UUID sessionId;
    private final Money discountAmount;
    private final Instant reservedAt;
    private final Instant expiresAt;

    private UUID orderId;
    private RedemptionStatus status;
    private Instant confirmedAt;

    private PromoRedemption(
        RedemptionId id,
        UUID sessionId,
        Money discountAmount,
        Instant reservedAt,
        Instant expiresAt,
        UUID orderId,
        RedemptionStatus status,
        Instant confirmedAt
    ) {
        this.id = Objects.requireNonNull(id, "RedemptionId cannot be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
        this.discountAmount = Objects.requireNonNull(discountAmount, "discountAmount cannot be null");
        this.reservedAt = Objects.requireNonNull(reservedAt, "reservedAt cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        this.orderId = orderId;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.confirmedAt = confirmedAt;

        if (!expiresAt.isAfter(reservedAt)) {
            throw new IllegalArgumentException("Redemption must expire after it is reserved");
        }

        if (status == RedemptionStatus.CONFIRMED && (orderId == null || confirmedAt == null)) {
            throw new IllegalStateException("Confirmed redemption must have an orderId and confirmedAt");
        }
    }

    public static PromoRedemption reserve(UUID sessionId, Money discountAmount, Instant now, Instant expiresAt) {
        Objects.requireNonNull(now, "now cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");

        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Redemption expiry must be in the future");
        }

        return new PromoRedemption(
            RedemptionId.generate(),
            sessionId,
            discountAmount,
            now,
            expiresAt,
            null,
            RedemptionStatus.RESERVED,
            null
        );
    }

    public static PromoRedemption rehydrate(
        RedemptionId id,
        UUID sessionId,
        Money discountAmount,
        Instant reservedAt,
        Instant expiresAt,
        UUID orderId,
        RedemptionStatus status,
        Instant confirmedAt
    ) {
        return new PromoRedemption(id, sessionId, discountAmount, reservedAt, expiresAt, orderId, status, confirmedAt);
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

    public boolean isActiveReservationFor(UUID sessionId, Instant now) {
        return status == RedemptionStatus.RESERVED && this.sessionId.equals(sessionId) && !isExpiredAt(now);
    }

    private void ensureReserved() {
        if (status != RedemptionStatus.RESERVED) {
            throw new InvalidCampaignStateException("Redemption is not reserved: " + status);
        }
    }

    public RedemptionId getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
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
