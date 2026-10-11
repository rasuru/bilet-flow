package com.biletflow.biletflow.paymentsmonetization.domain.activation;

import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePayment;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePaymentId;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.ActivationNotAllowedException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for the paid-sales activation of one event.
 *
 * <p>There is never more than one activation per event. The eventId is owned by
 * Event Management and held here only as an opaque foreign identifier.
 *
 * <p>The paid-sales checklist (the event exists, belongs to this organizer and is
 * in a state that allows paid sales) is answered by Event Management and passed
 * in by the application layer.
 */
public class EventPaidSalesActivation {

    private final UUID eventId;
    private final Long organizerId;
    private final Instant createdAt;
    private EventPaidSalesActivationStatus status;
    private ActivationFeePaymentId feePaymentId;
    private Instant suspendedAt;
    private String suspensionReason;

    public EventPaidSalesActivation(
        UUID eventId,
        Long organizerId,
        Instant createdAt,
        EventPaidSalesActivationStatus status,
        ActivationFeePaymentId feePaymentId,
        Instant suspendedAt,
        String suspensionReason
    ) {
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.feePaymentId = feePaymentId;
        this.suspendedAt = suspendedAt;
        this.suspensionReason = suspensionReason;
    }

    public static EventPaidSalesActivation request(UUID eventId, Long organizerId, Instant now) {
        return new EventPaidSalesActivation(eventId, organizerId, now, EventPaidSalesActivationStatus.PENDING, null, null, null);
    }

    public void activate(ActivationFeePayment feePayment, boolean checklistSatisfied) {
        Objects.requireNonNull(feePayment, "feePayment cannot be null");

        if (status != EventPaidSalesActivationStatus.PENDING) {
            throw new ActivationNotAllowedException("Paid sales for this event are already " + status);
        }

        if (!feePayment.getEventId().equals(eventId)) {
            throw new ActivationNotAllowedException("Activation fee payment belongs to another event");
        }

        if (!feePayment.hasSucceeded()) {
            throw new ActivationNotAllowedException("Activation fee has not been paid for event " + eventId);
        }

        if (!checklistSatisfied) {
            throw new ActivationNotAllowedException("Paid-sales checklist is not satisfied for event " + eventId);
        }

        this.feePaymentId = feePayment.getId();
        this.status = EventPaidSalesActivationStatus.ACTIVE;
    }

    public void suspend(String reason, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (status != EventPaidSalesActivationStatus.ACTIVE) {
            throw new ActivationNotAllowedException("Only active paid sales can be suspended, current status: " + status);
        }

        this.status = EventPaidSalesActivationStatus.SUSPENDED;
        this.suspendedAt = now;
        this.suspensionReason = reason;
    }

    /**
     * Does not re-check the paid-sales checklist and charges no new fee: the fee
     * paid for the original activation survives the suspension.
     */
    public void reinstate() {
        if (status != EventPaidSalesActivationStatus.SUSPENDED) {
            throw new ActivationNotAllowedException("Only suspended paid sales can be reinstated, current status: " + status);
        }

        this.status = EventPaidSalesActivationStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspensionReason = null;
    }

    public boolean permitsPaidSales() {
        return status == EventPaidSalesActivationStatus.ACTIVE;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public EventPaidSalesActivationStatus getStatus() {
        return status;
    }

    public ActivationFeePaymentId getFeePaymentId() {
        return feePaymentId;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }
}
