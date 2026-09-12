package com.biletflow.biletflow.payments.domain.payment;

public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    PARTIALLY_REFUNDED,
    FULLY_REFUNDED;

    /**
     * True when the payment may still receive refunds.
     */
    public boolean allowsRefund() {
        return this == SUCCEEDED || this == PARTIALLY_REFUNDED;
    }

    /**
     * True when money actually moved for this payment, regardless of whether it
     * was later refunded. This is the predicate that decides whether an order
     * has already been charged.
     */
    public boolean isSettled() {
        return this == SUCCEEDED || this == PARTIALLY_REFUNDED || this == FULLY_REFUNDED;
    }
}
