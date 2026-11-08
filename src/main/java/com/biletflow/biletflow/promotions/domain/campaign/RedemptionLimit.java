package com.biletflow.biletflow.promotions.domain.campaign;

public record RedemptionLimit(Integer maxRedemptions) {
    public RedemptionLimit {
        if (maxRedemptions != null && maxRedemptions <= 0) {
            throw new IllegalArgumentException("Maximum redemptions must be greater than 0");
        }
    }

    public static RedemptionLimit unlimited() {
        return new RedemptionLimit(null);
    }

    public static RedemptionLimit of(int maxRedemptions) {
        return new RedemptionLimit(maxRedemptions);
    }

    public boolean isUnlimited() {
        return maxRedemptions == null;
    }

    public boolean isReachedBy(int usedRedemptions) {
        return !isUnlimited() && usedRedemptions >= maxRedemptions;
    }
}
