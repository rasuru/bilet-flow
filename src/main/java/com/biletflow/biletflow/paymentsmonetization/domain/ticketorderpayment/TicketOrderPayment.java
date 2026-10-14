package com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.InvalidPaymentStateException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root for an attendee paying for one ticket order.
 *
 * <p>A payment has at most one refund, which always returns the full amount.
 * A failed refund may be replaced by a new request; a completed one is final.
 * Payment and refund outcomes arrive asynchronously from the provider.
 */
public class TicketOrderPayment {

    private final TicketOrderPaymentId id;
    private final UUID orderId;
    private final UUID eventId;
    private final Long organizerId;
    private final Money amount;
    private TicketOrderPaymentStatus status;
    private String providerReference;
    private Instant paidAt;
    private Instant failedAt;
    private String failureReason;
    private Refund refund;

    public TicketOrderPayment(
        TicketOrderPaymentId id,
        UUID orderId,
        UUID eventId,
        Long organizerId,
        Money amount,
        TicketOrderPaymentStatus status,
        String providerReference,
        Instant paidAt,
        Instant failedAt,
        String failureReason,
        Refund refund
    ) {
        this.id = Objects.requireNonNull(id, "TicketOrderPaymentId cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId cannot be null");
        this.amount = Objects.requireNonNull(amount, "Payment amount cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.providerReference = providerReference;
        this.paidAt = paidAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.refund = refund;
    }

    public static TicketOrderPayment initiate(UUID orderId, UUID eventId, Long organizerId, Money amount) {
        Objects.requireNonNull(amount, "Payment amount cannot be null");
        if (amount.isZero()) {
            throw new InvalidPaymentStateException("Cannot initiate a payment for a zero amount");
        }
        return new TicketOrderPayment(
            TicketOrderPaymentId.generate(),
            orderId,
            eventId,
            organizerId,
            amount,
            TicketOrderPaymentStatus.PENDING,
            null,
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

        this.status = TicketOrderPaymentStatus.SUCCEEDED;
        this.providerReference = providerReference;
        this.paidAt = now;
    }

    public void fail(String reason, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        ensurePending();

        this.status = TicketOrderPaymentStatus.FAILED;
        this.failureReason = reason;
        this.failedAt = now;
    }

    public Refund requestRefund(String reason, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (!receivesRefunds()) {
            throw new InvalidPaymentStateException("Payment " + id.value() + " cannot receive a refund");
        }

        this.refund = Refund.request(reason, now);
        return refund;
    }

    public void completeRefund(String providerReference, Instant now) {
        requireRefund().complete(providerReference, now);
        this.status = TicketOrderPaymentStatus.REFUNDED;
    }

    public void failRefund(String reason, Instant now) {
        requireRefund().fail(reason, now);
    }

    public boolean receivesRefunds() {
        return status == TicketOrderPaymentStatus.SUCCEEDED && (refund == null || refund.hasFailed());
    }

    public boolean hasBeenCharged() {
        return status == TicketOrderPaymentStatus.SUCCEEDED || status == TicketOrderPaymentStatus.REFUNDED;
    }

    private Refund requireRefund() {
        if (refund == null) {
            throw new InvalidPaymentStateException("Payment " + id.value() + " has no refund in progress");
        }
        return refund;
    }

    private void ensurePending() {
        if (status != TicketOrderPaymentStatus.PENDING) {
            throw new InvalidPaymentStateException("Payment is no longer pending: " + status);
        }
    }

    public TicketOrderPaymentId getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
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

    public TicketOrderPaymentStatus getStatus() {
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

    public Optional<Refund> getRefund() {
        return Optional.ofNullable(refund);
    }
}
