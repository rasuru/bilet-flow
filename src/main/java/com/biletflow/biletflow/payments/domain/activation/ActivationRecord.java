package com.biletflow.biletflow.payments.domain.activation;

import com.biletflow.biletflow.payments.domain.exceptions.ActivationNotAllowedException;
import com.biletflow.biletflow.payments.domain.payment.PaymentId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for the paid-sales activation state of one event.
 *
 * <p>Identified by eventId: activation is event-scoped and there is never more
 * than one record per event. The eventId itself is owned by Event Management and
 * held here only as an opaque foreign identifier.
 */
public class ActivationRecord {

    private final UUID eventId;
    private final Long organizerId;
    private ActivationStatus status;
    private PaymentId feePaymentId;
    private Instant activatedAt;
    private Instant suspendedAt;
    private String suspensionReason;

    public ActivationRecord(
        UUID eventId,
        Long organizerId,
        ActivationStatus status,
        PaymentId feePaymentId,
        Instant activatedAt,
        Instant suspendedAt,
        String suspensionReason
    ) {
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.feePaymentId = feePaymentId;
        this.activatedAt = activatedAt;
        this.suspendedAt = suspendedAt;
        this.suspensionReason = suspensionReason;
    }

    public static ActivationRecord request(UUID eventId, Long organizerId) {
        return new ActivationRecord(eventId, organizerId, ActivationStatus.PENDING, null, null, null, null);
    }

    /**
     * PENDING -> ACTIVE. Requires both prerequisites: a settled activation-fee
     * payment, and an organizer who satisfies verification/payout checks.
     * Organizer eligibility is fetched by the application layer and passed in;
     * this aggregate owns only the rule, not the fact.
     */
    public void activate(PaymentId feePaymentId, boolean organizerEligible, Instant now) {
        Objects.requireNonNull(feePaymentId, "feePaymentId cannot be null — the activation fee must be settled first");
        Objects.requireNonNull(now, "now cannot be null");

        if (status != ActivationStatus.PENDING) {
            throw new ActivationNotAllowedException("Paid sales for this event are already " + status);
        }

        if (!organizerEligible) {
            throw new ActivationNotAllowedException("Organizer is not verified or payout-ready");
        }

        this.feePaymentId = feePaymentId;
        this.status = ActivationStatus.ACTIVE;
        this.activatedAt = now;
    }

    public void suspend(String reason, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (status != ActivationStatus.ACTIVE) {
            throw new ActivationNotAllowedException("Only active paid sales can be suspended, current status: " + status);
        }

        this.status = ActivationStatus.SUSPENDED;
        this.suspendedAt = now;
        this.suspensionReason = reason;
    }

    /**
     * SUSPENDED -> ACTIVE. Deliberately does not re-check organizer eligibility
     * and charges no new fee: the fee was already settled before the original
     * activation and feePaymentId survives the suspension cycle.
     */
    public void reinstate(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (status != ActivationStatus.SUSPENDED) {
            throw new ActivationNotAllowedException("Only suspended paid sales can be reinstated, current status: " + status);
        }

        this.status = ActivationStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspensionReason = null;
    }

    public boolean permitsPaidSales() {
        return status.permitsPaidSales();
    }

    public boolean isFeeSettled() {
        return feePaymentId != null;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public ActivationStatus getStatus() {
        return status;
    }

    public PaymentId getFeePaymentId() {
        return feePaymentId;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }
}
