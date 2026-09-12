package com.biletflow.biletflow.payments.domain.payment;

import com.biletflow.biletflow.common.domain.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * A single refund recorded against a {@link Payment}.
 *
 * <p>Refunds are historical facts: once created they never change. They are
 * created only through {@code Payment.refund(...)}, which is what enforces the
 * invariant that the sum of refunds cannot exceed the amount charged.
 */

public class Refund {

    private final Money amount;
    private final String reason;
    private final Instant refundedAt;
    private final String providerReference;

    public Refund(Money amount, String reason, Instant refundedAt, String providerReference) {
        this.amount = Objects.requireNonNull(amount, "Refund amount cannot be null");
        this.reason = reason;
        this.refundedAt = Objects.requireNonNull(refundedAt, "refundedAt cannot be null");
        this.providerReference = Objects.requireNonNull(providerReference, "providerReference cannot be null");
    }

    static Refund create(Money amount, String reason, Instant refundedAt, String providerReference) {
        return new Refund(amount, reason, refundedAt, providerReference);
    }

    public Money getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public String getProviderReference() {
        return providerReference;
    }
}
