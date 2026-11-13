package com.biletflow.biletflow.promotions.persistence.campaign.entity;

import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Embeddable
public class PromoRedemptionJpaEmbeddable {

    @Column(name = "redemption_id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "discount_currency", nullable = false, length = 3)
    private String discountCurrency;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RedemptionStatus status;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected PromoRedemptionJpaEmbeddable() {}

    public PromoRedemptionJpaEmbeddable(
        UUID id,
        UUID sessionId,
        BigDecimal discountAmount,
        String discountCurrency,
        Instant reservedAt,
        Instant expiresAt,
        UUID orderId,
        RedemptionStatus status,
        Instant confirmedAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.discountAmount = discountAmount;
        this.discountCurrency = discountCurrency;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
        this.orderId = orderId;
        this.status = status;
        this.confirmedAt = confirmedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public String getDiscountCurrency() {
        return discountCurrency;
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
