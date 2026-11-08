package com.biletflow.biletflow.promotions.domain.exceptions;

import java.util.Objects;

public class PromoCodeRejectedException extends PromotionsDomainException {
    private final RejectionReason reason;

    public PromoCodeRejectedException(RejectionReason reason) {
        super(Objects.requireNonNull(reason, "reason cannot be null").defaultMessage());
        this.reason = reason;
    }

    public RejectionReason getReason() {
        return reason;
    }
}
