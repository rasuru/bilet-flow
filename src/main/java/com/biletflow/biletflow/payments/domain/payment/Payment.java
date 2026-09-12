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
 * Aggregate root for the financial record of one charge against one order.
 *
 * <p>Owns the invariant that the sum of all refunds can never exceed the amount
 * charged. Refunds live inside this aggregate precisely because that rule spans
 * the payment and all of its refunds.
 */
public class Payment {

    private final PaymentId id;
    private final UUID orderId;
    private final Money amount;
    private PaymentStatus status;
    private String providerReference;
    private Instant paidAt;
    private Instant failedAt;
    private String failureReason;
    private final List<Refund> refunds;

    public Payment(
        PaymentId id,
        UUID orderId,
        Money amount,
        PaymentStatus status,
        String providerReference,
        Instant paidAt,
        String failureReason,
        List<Refund> refunds
    ) {
        this.id = Objects.requireNonNull(id, "PaymentId cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.amount = Objects.requireNonNull(amount, "Payment amount cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.providerReference = providerReference;
        this.paidAt = paidAt;
        this.failureReason = failureReason;
        this.refunds = new ArrayList<>(refunds == null ? List.of() : refunds);
    }

    public static Payment initiate(UUID orderId, Money amount) {
        Objects.requireNonNull(amount, "Payment amount cannot be null");

        if (amount.isZero()) {
            throw new InvalidPaymentStateException("Cannot initiate a payment for a zero amount");
        }

        return new Payment(PaymentId.generate(), orderId, amount, PaymentStatus.PENDING, null, null, null, List.of());
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

    public Refund refund(Money requested, String reason, String providerReference, Instant now) {
        Objects.requireNonNull(requested, "Refund amount cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        if (!status.allowsRefund()) {
            throw new InvalidPaymentStateException("Cannot refund a payment with status " + status);
        }

        if (requested.isZero()) {
            throw new InvalidPaymentStateException("Refund amount must be greater than zero");
        }

        Money newTotal = refundedTotal().add(requested);

        if (newTotal.isGreaterThan(amount)) {
            throw new RefundExceedsPaymentException(
                "Refunding " + requested + " would exceed the charged amount " + amount
            );
        }

        Refund refund = Refund.create(requested, reason, now, providerReference);
        refunds.add(refund);
        this.status = amount.subtract(newTotal).isZero() ? PaymentStatus.FULLY_REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;

        return refund;
    }

    public Money refundedTotal() {
        return refunds.stream().map(Refund::getAmount).reduce(Money.zero(amount.currency()), Money::add);
    }

    public Money refundableRemaining() {
        return status.allowsRefund() ? amount.subtract(refundedTotal()) : Money.zero(amount.currency());
    }

    private void ensurePending() {
        if (status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException("Payment is no longer pending: " + status);
        }
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
}
