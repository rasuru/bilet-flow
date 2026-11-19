package com.biletflow.biletflow.promotions.ohs;

import java.util.Objects;

public class PromotionRejectedException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        NOT_YET_VALID,
        EXPIRED,
        DISABLED,
        CAMPAIGN_ENDED,
        EXHAUSTED,
        INAPPLICABLE,
    }

    private final Reason reason;

    public PromotionRejectedException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
    }

    public Reason getReason() {
        return reason;
    }
}
