package com.biletflow.biletflow.payments.domain.revenue;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.payments.domain.exceptions.InsufficientBalanceException;
import com.biletflow.biletflow.payments.domain.exceptions.InvalidPayoutStateException;
import java.util.Currency;
import java.util.Objects;

/**
 * Aggregate root for one organizer's payable revenue.
 *
 * <p>Successful ticket payments credit the balance, completed refunds debit it,
 * and payouts move money from available, through pending, to paid out. Owns the
 * invariant that a payout can never exceed the available balance.
 *
 * <p>Money cannot be negative, so a refund larger than the available balance
 * (for example, after a payout) is recorded as a deficit. Later credits pay the
 * deficit off before increasing the available balance, and no payout is possible
 * while a deficit exists because available stays at zero.
 */
public class OrganizerBalance {

    private final Long organizerId;
    private Money available;
    private Money pendingPayout;
    private Money paidOut;
    private Money deficit;

    public OrganizerBalance(Long organizerId, Money available, Money pendingPayout, Money paidOut, Money deficit) {
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId cannot be null");
        this.available = Objects.requireNonNull(available, "available cannot be null");
        this.pendingPayout = Objects.requireNonNull(pendingPayout, "pendingPayout cannot be null");
        this.paidOut = Objects.requireNonNull(paidOut, "paidOut cannot be null");
        this.deficit = Objects.requireNonNull(deficit, "deficit cannot be null");
    }

    public static OrganizerBalance open(Long organizerId, Currency currency) {
        Objects.requireNonNull(currency, "currency cannot be null");
        Money zero = Money.zero(currency);
        return new OrganizerBalance(organizerId, zero, zero, zero, zero);
    }

    /**
     * A ticket payment for one of this organizer's events succeeded.
     */
    public void credit(Money amount) {
        requirePositive(amount);

        if (deficit.isZero()) {
            available = available.add(amount);
        } else if (amount.isGreaterThan(deficit)) {
            available = available.add(amount.subtract(deficit));
            deficit = zero();
        } else {
            deficit = deficit.subtract(amount);
        }
    }

    /**
     * A refund of a ticket payment completed.
     */
    public void debit(Money amount) {
        requirePositive(amount);

        if (amount.isGreaterThan(available)) {
            deficit = deficit.add(amount.subtract(available));
            available = zero();
        } else {
            available = available.subtract(amount);
        }
    }

    /**
     * Moves money from available to pending when a payout is requested.
     */
    public void reserveForPayout(Money amount) {
        requirePositive(amount);

        if (amount.isGreaterThan(available)) {
            throw new InsufficientBalanceException(
                "Payout of " + amount + " exceeds available balance " + available + " for organizer " + organizerId
            );
        }

        available = available.subtract(amount);
        pendingPayout = pendingPayout.add(amount);
    }

    /**
     * The payout completed: pending money is now paid out.
     */
    public void settlePayout(Money amount) {
        requireWithinPending(amount);
        pendingPayout = pendingPayout.subtract(amount);
        paidOut = paidOut.add(amount);
    }

    /**
     * The payout failed: pending money returns to the balance. Goes through
     * credit so any deficit created in the meantime is paid off first.
     */
    public void releasePayout(Money amount) {
        requireWithinPending(amount);
        pendingPayout = pendingPayout.subtract(amount);
        credit(amount);
    }

    public boolean hasDeficit() {
        return !deficit.isZero();
    }

    private Money zero() {
        return Money.zero(available.currency());
    }

    private void requirePositive(Money amount) {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (amount.isZero()) {
            throw new InvalidPayoutStateException("Amount must be greater than zero");
        }
    }

    private void requireWithinPending(Money amount) {
        requirePositive(amount);
        if (amount.isGreaterThan(pendingPayout)) {
            throw new InvalidPayoutStateException(
                "Amount " + amount + " exceeds pending payout " + pendingPayout + " for organizer " + organizerId
            );
        }
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public Money getAvailable() {
        return available;
    }

    public Money getPendingPayout() {
        return pendingPayout;
    }

    public Money getPaidOut() {
        return paidOut;
    }

    public Money getDeficit() {
        return deficit;
    }
}
