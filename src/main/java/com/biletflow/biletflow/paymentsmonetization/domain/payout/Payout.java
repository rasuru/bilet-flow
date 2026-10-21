package com.biletflow.biletflow.paymentsmonetization.domain.payout;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.InvalidPayoutStateException;
import com.biletflow.biletflow.paymentsmonetization.domain.revenue.OrganizerBalance;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root for one simulated transfer of payable revenue to an organizer.
 *
 * <p>A payout is requested first and completed or failed later, when the
 * simulated provider reports the outcome. Whether the organizer has enough money
 * is not decided here: OrganizerBalance validates eligibility and a persisted
 * REQUESTED payout reserves the amount.
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
        return request(PayoutId.generate(), organizerId, amount, now);
    }

    public static Payout request(PayoutId id, Long organizerId, Money amount, Instant now) {
        Objects.requireNonNull(amount, "Payout amount cannot be null");
        if (!Money.KZT.equals(amount.currency())) throw new IllegalArgumentException("Payouts use KZT");
        if (organizerId == null || organizerId <= 0) throw new IllegalArgumentException("Invalid organizer");
        if (amount.isZero()) {
            throw new InvalidPayoutStateException("Payout amount must be greater than zero");
        }
        return new Payout(id, organizerId, amount, now, PayoutStatus.REQUESTED, null, null, null);
    }

    public void requireSameAmount(Money amount) {
        if (!this.amount.equals(amount)) throw new InvalidPayoutStateException("Payout request ID was already used for a different amount");
    }

    /** Simulation never calls a bank. Recheck eligibility before completing a reserved transfer. */
    public void simulate(OrganizerBalance balance, boolean successful, Instant now) {
        if (balance.getOrganizerId() != organizerId) throw new IllegalArgumentException("Payout balance belongs to another organizer");
        if (balance.hasDebt() || balance.isPayoutsBlocked()) {
            fail("Revenue changed or reconciliation is pending; no simulated transfer was made", now);
        } else if (!successful) {
            fail("Simulated payout provider rejected the transfer", now);
        } else {
            complete("SIM-" + id.value(), now);
        }
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
