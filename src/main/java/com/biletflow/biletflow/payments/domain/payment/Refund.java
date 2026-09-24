package com.biletflow.biletflow.payments.domain.payment;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.payments.domain.exceptions.InvalidPaymentStateException;
import java.time.Instant;
import java.util.Objects;

/**
 * A single refund recorded against a {@link Payment}.
 *
 * <p>A refund is requested first and completed or failed later, when the
 * payment provider reports the outcome asynchronously. Only a COMPLETED refund
 * means money was returned; a FAILED refund returns nothing.
 *
 * <p>Refunds are created and changed only through {@link Payment}, which is what
 * enforces the invariant that completed plus in-flight refunds cannot exceed the
 * amount charged.
 */
public class Refund {

    private final RefundId id;
    private final Money amount;
    private final String reason;
    private final Instant requestedAt;
    private RefundStatus status;
    private String providerReference;
    private String failureReason;
    private Instant completedAt;

    private Refund(RefundId id, Money amount, String reason, Instant requestedAt) {
        this.id = Objects.requireNonNull(id, "Refund id cannot be null");
        this.amount = Objects.requireNonNull(amount, "Refund amount cannot be null");
        this.reason = reason;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt cannot be null");
        this.status = RefundStatus.REQUESTED;
    }

    static Refund request(Money amount, String reason, Instant requestedAt) {
        return new Refund(RefundId.generate(), amount, reason, requestedAt);
    }

    void complete(String providerReference, Instant completedAt) {
        requireRequested();
        this.status = RefundStatus.COMPLETED;
        this.providerReference = Objects.requireNonNull(providerReference, "providerReference cannot be null");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt cannot be null");
    }

    void fail(String failureReason, Instant completedAt) {
        requireRequested();
        this.status = RefundStatus.FAILED;
        this.failureReason = failureReason;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt cannot be null");
    }

    private void requireRequested() {
        if (status != RefundStatus.REQUESTED) {
            throw new InvalidPaymentStateException("Refund " + id.value() + " is already " + status);
        }
    }

    public RefundId getId() {
        return id;
    }

    public Money getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
