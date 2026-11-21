package com.biletflow.biletflow.promotions.domain.promocode;

import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import com.biletflow.biletflow.promotions.domain.exceptions.PromoCodeRejectedException;
import com.biletflow.biletflow.promotions.domain.exceptions.RejectionReason;
import java.time.Instant;
import java.util.Objects;

public class PromoCode {
    private final PromoCodeId id;
    private final CampaignId campaignId;
    private final CodeValue code;
    private final CampaignToken token;
    private final Instant createdAt;

    private boolean enabled;
    private Instant disabledAt;

    private PromoCode(
        PromoCodeId id,
        CampaignId campaignId,
        CodeValue code,
        CampaignToken token,
        Instant createdAt,
        boolean enabled,
        Instant disabledAt
    ) {
        this.id = Objects.requireNonNull(id, "PromoCodeId cannot be null");
        this.campaignId = Objects.requireNonNull(campaignId, "CampaignId cannot be null");
        this.code = Objects.requireNonNull(code, "code cannot be null");
        this.token = Objects.requireNonNull(token, "token cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.enabled = enabled;
        this.disabledAt = disabledAt;

        if (enabled == (disabledAt != null)) {
            throw new IllegalStateException("disabledAt must be set exactly when the promo code is disabled");
        }
    }

    public static PromoCode generateFor(CampaignId campaignId, Instant now) {
        return create(campaignId, CodeValue.generate(), now);
    }

    public static PromoCode create(CampaignId campaignId, CodeValue code, Instant now) {
        return new PromoCode(PromoCodeId.generate(), campaignId, code, CampaignToken.generate(), now, true, null);
    }

    public static PromoCode rehydrate(
        PromoCodeId id,
        CampaignId campaignId,
        CodeValue code,
        CampaignToken token,
        Instant createdAt,
        boolean enabled,
        Instant disabledAt
    ) {
        return new PromoCode(id, campaignId, code, token, createdAt, enabled, disabledAt);
    }

    public void disable(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (!enabled) {
            throw new InvalidCampaignStateException("Promo code is already disabled");
        }

        enabled = false;
        disabledAt = now;
    }

    public void enable() {
        if (enabled) {
            throw new InvalidCampaignStateException("Promo code is already enabled");
        }

        enabled = true;
        disabledAt = null;
    }

    public void ensureRedeemable() {
        if (!enabled) {
            throw new PromoCodeRejectedException(RejectionReason.DISABLED);
        }
    }

    public PromoCodeId getId() {
        return id;
    }

    public CampaignId getCampaignId() {
        return campaignId;
    }

    public CodeValue getCode() {
        return code;
    }

    public CampaignToken getToken() {
        return token;
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
}
