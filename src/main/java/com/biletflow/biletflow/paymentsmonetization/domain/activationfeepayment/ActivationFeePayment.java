package com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.InvalidPaymentStateException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for an organizer paying the paid-sales activation fee for one event.
 *
 * <p>Deliberately separate from ticket-order payments: it never becomes organizer
 * revenue, and whether it can be refunded is an open policy decision, so it has no
 * refund behavior yet.
 */
public class ActivationFeePayment {

    private final ActivationFeePaymentId id;
    private final UUID eventId;
    private final Long organizerId;
    private final Money amount;
    private ActivationFeePaymentStatus status;
    private String providerReference;
    private Instant paidAt;
    private Instant failedAt;
    private String failureReason;

    public ActivationFeePayment(
        ActivationFeePaymentId id,
        UUID eventId,
        Long organizerId,
        Money amount,
        ActivationFeePaymentStatus status,
        String providerReference,
        Instant paidAt,
        Instant failedAt,
        String failureReason
    ) {
        this.id = Objects.requireNonNull(id, "ActivationFeePaymentId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId cannot be null");
        this.amount = Objects.requireNonNull(amount, "Payment amount cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.providerReference = providerReference;
        this.paidAt = paidAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
    }

    public static ActivationFeePayment initiate(UUID eventId, Long organizerId, Money amount) {
        Objects.requireNonNull(amount, "Payment amount cannot be null");
        if (amount.isZero()) {
            throw new InvalidPaymentStateException("Cannot initiate a payment for a zero amount");
        }
        return new ActivationFeePayment(
            ActivationFeePaymentId.generate(),
            eventId,
            organizerId,
            amount,
            ActivationFeePaymentStatus.PENDING,
            null,
            null,
            null,
            null
        );
    }

    public void succeed(String providerReference, Instant now) {
        Objects.requireNonNull(providerReference, "providerReference cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        ensurePending();

        this.status = ActivationFeePaymentStatus.SUCCEEDED;
        this.providerReference = providerReference;
        this.paidAt = now;
    }

    public void reconcileCharge(String reference, Instant now) {
        Objects.requireNonNull(reference);
        Objects.requireNonNull(now);
        if (paidAt != null) {
            if (!reference.equals(providerReference)) throw new InvalidPaymentStateException("Conflicting provider transaction");
            return;
        }
        status = ActivationFeePaymentStatus.SUCCEEDED;
        providerReference = reference;
        paidAt = now;
        failedAt = null;
        failureReason = null;
    }

    public void fail(String reason, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        ensurePending();

        this.status = ActivationFeePaymentStatus.FAILED;
        this.failureReason = reason;
        this.failedAt = now;
    }

    /** System compensation for a fee that cannot activate sales; no customer fee-refund policy is implied. */
    public void requestCompensatingRefund() {
        if (status == ActivationFeePaymentStatus.REFUND_REQUESTED || status == ActivationFeePaymentStatus.REFUNDED) return;
        if (status != ActivationFeePaymentStatus.SUCCEEDED) throw new InvalidPaymentStateException("Fee has not been captured");
        status = ActivationFeePaymentStatus.REFUND_REQUESTED;
    }

    public void completeCompensatingRefund() {
        if (status == ActivationFeePaymentStatus.REFUNDED) return;
        if (status != ActivationFeePaymentStatus.REFUND_REQUESTED) throw new InvalidPaymentStateException("Fee refund was not requested");
        status = ActivationFeePaymentStatus.REFUNDED;
    }

    public boolean hasSucceeded() {
        return status == ActivationFeePaymentStatus.SUCCEEDED;
    }

    private void ensurePending() {
        if (status != ActivationFeePaymentStatus.PENDING) {
            throw new InvalidPaymentStateException("Payment is no longer pending: " + status);
        }
    }

    public ActivationFeePaymentId getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public Money getAmount() {
        return amount;
    }

    public ActivationFeePaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
