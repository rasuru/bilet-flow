package com.biletflow.biletflow.payments.domain.payout;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.payments.domain.exceptions.InvalidPayoutStateException;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root for one simulated transfer of payable revenue to an organizer.
 *
 * <p>A payout is requested first and completed or failed later, when the
 * simulated provider reports the outcome. Whether the organizer has enough money
 * is not decided here: that rule belongs to OrganizerBalance, which must reserve
 * the amount before a payout is requested.
 */
public class Payout {

    private final PayoutId id;
    private final Long organizerId;
    private final Money amount;
    private final Instant requestedAt;
    private PayoutStatus status;
    private String providerReference;
    private String failureReason;
    private Instant completedAt;

    public Payout(
        PayoutId id,
        Long organizerId,
        Money amount,
        Instant requestedAt,
        PayoutStatus status,
        String providerReference,
        String failureReason,
        Instant completedAt
    ) {
        this.id = Objects.requireNonNull(id, "PayoutId cannot be null");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId cannot be null");
        this.amount = Objects.requireNonNull(amount, "Payout amount cannot be null");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.providerReference = providerReference;
        this.failureReason = failureReason;
        this.completedAt = completedAt;
    }

    public static Payout request(Long organizerId, Money amount, Instant now) {
        Objects.requireNonNull(amount, "Payout amount cannot be null");
        if (amount.isZero()) {
            throw new InvalidPayoutStateException("Payout amount must be greater than zero");
        }
        return new Payout(PayoutId.generate(), organizerId, amount, now, PayoutStatus.REQUESTED, null, null, null);
    }

    public void complete(String providerReference, Instant now) {
        Objects.requireNonNull(providerReference, "providerReference cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        ensureRequested();

        this.status = PayoutStatus.COMPLETED;
        this.providerReference = providerReference;
        this.completedAt = now;
    }

    public void fail(String reason, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        ensureRequested();

        this.status = PayoutStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = now;
    }

    private void ensureRequested() {
        if (status != PayoutStatus.REQUESTED) {
            throw new InvalidPayoutStateException("Payout " + id.value() + " is already " + status);
        }
    }

    public PayoutId getId() {
        return id;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public Money getAmount() {
        return amount;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public PayoutStatus getStatus() {
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
