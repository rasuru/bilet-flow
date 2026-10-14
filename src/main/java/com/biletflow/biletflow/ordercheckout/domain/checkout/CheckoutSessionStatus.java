package com.biletflow.biletflow.ordercheckout.domain.checkout;

public enum CheckoutSessionStatus {
    IN_PROGRESS,
    AWAITING_PAYMENT,
    COMPLETED,
    EXPIRED,
    PAYMENT_FAILED,
    CANCELLED,
}
