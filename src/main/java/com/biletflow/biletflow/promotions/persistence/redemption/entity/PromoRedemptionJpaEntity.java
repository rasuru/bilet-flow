package com.biletflow.biletflow.promotions.persistence.redemption.entity;

import com.biletflow.biletflow.promotions.domain.redemption.RedemptionSource;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promotions_redemption")
public class PromoRedemptionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(name = "promo_code_id", nullable = false, updatable = false)
    private UUID promoCodeId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 16)
    private RedemptionSource source;

    @Column(name = "ticket_quantity", nullable = false, updatable = false)
    private int ticketQuantity;

    @Column(name = "gross_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "reserved_at", nullable = false, updatable = false)
    private Instant reservedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RedemptionStatus status;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PromoRedemptionJpaEntity() {}

    public PromoRedemptionJpaEntity(
        UUID id,
        UUID campaignId,
        UUID promoCodeId,
        UUID sessionId,
        RedemptionSource source,
        int ticketQuantity,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        String currency,
        Instant reservedAt,
        Instant expiresAt
    ) {
        this.id = id;
        this.campaignId = campaignId;
        this.promoCodeId = promoCodeId;
        this.sessionId = sessionId;
        this.source = source;
        this.ticketQuantity = ticketQuantity;
        this.grossAmount = grossAmount;
        this.discountAmount = discountAmount;
        this.currency = currency;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
    }

    public void replaceState(RedemptionStatus status, UUID orderId, Instant confirmedAt) {
        this.status = status;
        this.orderId = orderId;
        this.confirmedAt = confirmedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getPromoCodeId() {
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

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public String getCurrency() {
        return currency;
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

    public long getVersion() {
        return version;
    }
}
