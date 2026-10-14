package com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment;

import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.InvalidPaymentStateException;
import java.time.Instant;
import java.util.Objects;

public class Refund {

    private final RefundId id;
    private final String reason;
    private final Instant requestedAt;
    private RefundStatus status;
    private String providerReference;
    private String failureReason;
    private Instant completedAt;

    public Refund(
        RefundId id,
        String reason,
        Instant requestedAt,
        RefundStatus status,
        String providerReference,
        String failureReason,
        Instant completedAt
    ) {
        this.id = Objects.requireNonNull(id, "Refund id cannot be null");
        this.reason = reason;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.providerReference = providerReference;
        this.failureReason = failureReason;
        this.completedAt = completedAt;
    }

    static Refund request(String reason, Instant requestedAt) {
        return new Refund(RefundId.generate(), reason, requestedAt, RefundStatus.REQUESTED, null, null, null);
    }

    void complete(String providerReference, Instant completedAt) {
        requireRequested();
        Objects.requireNonNull(providerReference, "providerReference cannot be null");
        Objects.requireNonNull(completedAt, "completedAt cannot be null");

        this.status = RefundStatus.COMPLETED;
        this.providerReference = providerReference;
        this.completedAt = completedAt;
    }

    void fail(String failureReason, Instant completedAt) {
        requireRequested();
        Objects.requireNonNull(completedAt, "completedAt cannot be null");

        this.status = RefundStatus.FAILED;
        this.failureReason = failureReason;
        this.completedAt = completedAt;
    }

    boolean hasFailed() {
        return status == RefundStatus.FAILED;
    }

    private void requireRequested() {
        if (status != RefundStatus.REQUESTED) {
            throw new InvalidPaymentStateException("Refund " + id.value() + " is already " + status);
        }
    }

    public RefundId getId() {
        return id;
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
