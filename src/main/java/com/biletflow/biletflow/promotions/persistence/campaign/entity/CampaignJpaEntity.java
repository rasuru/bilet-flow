package com.biletflow.biletflow.promotions.persistence.campaign.entity;

import com.biletflow.biletflow.promotions.domain.campaign.CampaignStatus;
import com.biletflow.biletflow.promotions.domain.campaign.DiscountType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "promotions_campaign")
public class CampaignJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "name", nullable = false, updatable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, updatable = false, length = 16)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private Instant validUntil;

    @Column(name = "max_redemptions", updatable = false)
    private Integer maxRedemptions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CampaignStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "promotions_campaign_ticket_type", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "ticket_type_id", nullable = false, updatable = false)
    private Set<UUID> applicableTicketTypeIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "promotions_campaign_redemption", joinColumns = @JoinColumn(name = "campaign_id"))
    private List<PromoRedemptionJpaEmbeddable> redemptions = new ArrayList<>();

    protected CampaignJpaEntity() {}

    public CampaignJpaEntity(
        UUID id,
        UUID eventId,
        String name,
        DiscountType discountType,
        BigDecimal discountValue,
        Instant validFrom,
        Instant validUntil,
        Integer maxRedemptions,
        Instant createdAt,
        Set<UUID> applicableTicketTypeIds
    ) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.maxRedemptions = maxRedemptions;
        this.createdAt = createdAt;
        this.applicableTicketTypeIds.addAll(applicableTicketTypeIds);
    }

    public void replaceState(CampaignStatus status, Instant endedAt, List<PromoRedemptionJpaEmbeddable> redemptions) {
        this.status = status;
        this.endedAt = endedAt;

        this.redemptions.clear();
        this.redemptions.addAll(redemptions);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public long getVersion() {
        return version;
    }

    public Set<UUID> getApplicableTicketTypeIds() {
        return applicableTicketTypeIds;
    }

    public List<PromoRedemptionJpaEmbeddable> getRedemptions() {
        return redemptions;
    }
}
