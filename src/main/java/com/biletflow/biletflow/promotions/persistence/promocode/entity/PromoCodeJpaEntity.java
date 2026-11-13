package com.biletflow.biletflow.promotions.persistence.promocode.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "promotions_promo_code",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_promotions_promo_code_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_promotions_promo_code_token", columnNames = "campaign_token"),
    }
)
public class PromoCodeJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "campaign_token", nullable = false, updatable = false)
    private UUID campaignToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PromoCodeJpaEntity() {}

    public PromoCodeJpaEntity(UUID id, UUID campaignId, String code, UUID campaignToken, Instant createdAt) {
        this.id = id;
        this.campaignId = campaignId;
        this.code = code;
        this.campaignToken = campaignToken;
        this.createdAt = createdAt;
    }

    public void replaceState(boolean enabled, Instant disabledAt) {
        this.enabled = enabled;
        this.disabledAt = disabledAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public String getCode() {
        return code;
    }

    public UUID getCampaignToken() {
        return campaignToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public long getVersion() {
        return version;
    }
}
