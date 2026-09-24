package com.biletflow.biletflow.payments.domain.payment;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.payments.domain.exceptions.InvalidPaymentStateException;
import com.biletflow.biletflow.payments.domain.exceptions.RefundExceedsPaymentException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for the financial record of one charge: either an attendee
 * paying for a ticket order, or an organizer paying the paid-sales activation fee.
 *
 * <p>Owns the invariant that completed refunds plus in-flight refund requests can
 * never exceed the amount charged. Refunds live inside this aggregate precisely
 * because that rule spans the payment and all of its refunds.
 *
 * <p>Payment and refund outcomes arrive asynchronously from the provider, so both
 * start pending and are completed or failed later.
 */
public class Payment {

    private final PaymentId id;
    private final PaymentPurpose purpose;
    private final UUID orderId;
    private final UUID eventId;
    private final Long organizerId;
    private final Money amount;
    private PaymentStatus status;
    private String providerReference;
    private Instant paidAt;
    private Instant failedAt;
    private String failureReason;
    private final List<Refund> refunds;

    public Payment(
        PaymentId id,
        PaymentPurpose purpose,
        UUID orderId,
        UUID eventId,
        Long organizerId,
        Money amount,
        PaymentStatus status,
        String providerReference,
        Instant paidAt,
        Instant failedAt,
        String failureReason,
        List<Refund> refunds
    ) {
        this.id = Objects.requireNonNull(id, "PaymentId cannot be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose cannot be null");
        if (purpose == PaymentPurpose.TICKET_ORDER) {
            Objects.requireNonNull(orderId, "orderId cannot be null for a ticket order payment");
        }
        this.orderId = orderId;
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId cannot be null");
        this.amount = Objects.requireNonNull(amount, "Payment amount cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.providerReference = providerReference;
        this.paidAt = paidAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.refunds = new ArrayList<>(refunds == null ? List.of() : refunds);
    }

    public static Payment initiateForOrder(UUID orderId, UUID eventId, Long organizerId, Money amount) {
        requireChargeable(amount);
        return new Payment(
            PaymentId.generate(), PaymentPurpose.TICKET_ORDER, orderId, eventId, organizerId,
            amount, PaymentStatus.PENDING, null, null, null, null, List.of()
        );
    }

    public static Payment initiateActivationFee(UUID eventId, Long organizerId, Money amount) {
        requireChargeable(amount);
        return new Payment(
            PaymentId.generate(), PaymentPurpose.ACTIVATION_FEE, null, eventId, organizerId,
            amount, PaymentStatus.PENDING, null, null, null, null, List.of()
        );
    }

    private static void requireChargeable(Money amount) {
        Objects.requireNonNull(amount, "Payment amount cannot be null");
        if (amount.isZero()) {
            throw new InvalidPaymentStateException("Cannot initiate a payment for a zero amount");
        }
    }

    public void succeed(String providerReference, Instant now) {
        Objects.requireNonNull(providerReference, "providerReference cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        ensurePending();

        this.status = PaymentStatus.SUCCEEDED;
        this.providerReference = providerReference;
        this.paidAt = now;
    }

    public void fail(String reason, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        ensurePending();

        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.failedAt = now;
    }

    public Refund requestRefund(Money requested, String reason, Instant now) {
        Objects.requireNonNull(requested, "Refund amount cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        if (!status.allowsRefund()) {
            throw new InvalidPaymentStateException("Cannot refund a payment with status " + status);
        }

        if (requested.isZero()) {
            throw new InvalidPaymentStateException("Refund amount must be greater than zero");
        }

        if (requested.isGreaterThan(refundableRemaining())) {
            throw new RefundExceedsPaymentException(
                "Refunding " + requested + " would exceed the refundable remainder " + refundableRemaining()
            );
        }

        Refund refund = Refund.request(requested, reason, now);
        refunds.add(refund);
        return refund;
    }

    public void completeRefund(RefundId refundId, String providerReference, Instant now) {
        findRefund(refundId).complete(providerReference, now);
        this.status = amount.subtract(refundedTotal()).isZero()
            ? PaymentStatus.FULLY_REFUNDED
            : PaymentStatus.PARTIALLY_REFUNDED;
    }

    public void failRefund(RefundId refundId, String reason, Instant now) {
        findRefund(refundId).fail(reason, now);
        // Status unchanged: no money moved, and the reserved amount is freed automatically.
    }

    public Money refundedTotal() {
        return sumRefunds(RefundStatus.COMPLETED);
    }

    public Money reservedForRefunds() {
        return sumRefunds(RefundStatus.REQUESTED);
    }

    public Money refundableRemaining() {
        return status.allowsRefund()
            ? amount.subtract(refundedTotal()).subtract(reservedForRefunds())
            : Money.zero(amount.currency());
    }

    private Money sumRefunds(RefundStatus wanted) {
        return refunds.stream()
            .filter(r -> r.getStatus() == wanted)
            .map(Refund::getAmount)
            .reduce(Money.zero(amount.currency()), Money::add);
    }

    private void ensurePending() {
        if (status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException("Payment is no longer pending: " + status);
        }
    }

    private Refund findRefund(RefundId refundId) {
        Objects.requireNonNull(refundId, "refundId cannot be null");
        return refunds.stream()
            .filter(r -> r.getId().equals(refundId))
            .findFirst()
            .orElseThrow(() -> new InvalidPaymentStateException(
                "Refund " + refundId.value() + " does not belong to payment " + id.value()
            ));
    }

    public boolean countsAsOrganizerRevenue() {
        return purpose == PaymentPurpose.TICKET_ORDER;
    }

    public PaymentId getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public List<Refund> getRefunds() {
        return Collections.unmodifiableList(refunds);
    }

    public PaymentPurpose getPurpose() {
        return purpose;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
