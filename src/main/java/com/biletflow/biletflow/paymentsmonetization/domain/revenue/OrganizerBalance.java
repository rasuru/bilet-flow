package com.biletflow.biletflow.paymentsmonetization.domain.revenue;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.InsufficientBalanceException;
import com.biletflow.biletflow.paymentsmonetization.domain.exceptions.InvalidPayoutStateException;
import java.math.BigDecimal;
import java.util.Objects;

/** Payable-funds snapshot rebuilt from the ledger and durable payout reservations. Owns payout eligibility rules. */
public final class OrganizerBalance {

    private final long organizerId;
    private final Money available;
    private final Money pendingPayout;
    private final Money paidOut;
    private final Money debt;
    private final boolean payoutsBlocked;

    private OrganizerBalance(long organizerId, BigDecimal netRevenue, Money pending, Money paid, boolean blocked) {
        if (organizerId <= 0) throw new IllegalArgumentException("Invalid organizer");
        Objects.requireNonNull(netRevenue, "netRevenue cannot be null");
        requireKzt(pending);
        requireKzt(paid);
        BigDecimal remaining = netRevenue.subtract(pending.amount()).subtract(paid.amount());
        this.organizerId = organizerId;
        this.available = Money.kzt(remaining.max(BigDecimal.ZERO));
        this.pendingPayout = pending;
        this.paidOut = paid;
        this.debt = Money.kzt(remaining.negate().max(BigDecimal.ZERO));
        this.payoutsBlocked = blocked;
    }

    public static OrganizerBalance fromRevenue(long organizerId, BigDecimal netRevenue, Money pending, Money paid, boolean blocked) {
        return new OrganizerBalance(organizerId, netRevenue, pending, paid, blocked);
    }

    /** The application's persisted REQUESTED payout reserves the validated amount. */
    public void requirePayoutAmount(Money amount) {
        requireKzt(amount);
        if (amount.isZero()) throw new InvalidPayoutStateException("Payout amount must be greater than zero");
        if (payoutsBlocked) throw new InvalidPayoutStateException(
            "Payouts are blocked while a ticket payment or refund requires reconciliation"
        );
        if (amount.isGreaterThan(available)) throw new InsufficientBalanceException("Payout exceeds the available organizer balance");
    }

    private static void requireKzt(Money amount) {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (!Money.KZT.equals(amount.currency())) throw new IllegalArgumentException("Payout balances use KZT");
    }

    public long getOrganizerId() {
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

    public Money getDebt() {
        return debt;
    }

    public boolean hasDebt() {
        return !debt.isZero();
    }

    public boolean isPayoutsBlocked() {
        return payoutsBlocked;
    }
}
