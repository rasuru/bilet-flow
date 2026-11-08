package com.biletflow.biletflow.promotions.domain.exceptions;

public enum RejectionReason {
    NOT_YET_VALID("This promo code is not valid yet"),
    EXPIRED("This promo code has expired"),
    DISABLED("This promo code has been disabled"),
    CAMPAIGN_ENDED("This promotional campaign has ended"),
    EXHAUSTED("This promo code has reached its redemption limit"),
    INAPPLICABLE("This promo code does not apply to the selected tickets");

    private final String defaultMessage;

    RejectionReason(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
