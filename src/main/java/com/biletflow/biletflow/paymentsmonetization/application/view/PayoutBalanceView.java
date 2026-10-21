package com.biletflow.biletflow.paymentsmonetization.application.view;

import com.biletflow.biletflow.paymentsmonetization.domain.revenue.OrganizerBalance;
import java.math.BigDecimal;

public record PayoutBalanceView(
    BigDecimal availableKzt,
    BigDecimal withheldKzt,
    BigDecimal pendingPayoutKzt,
    BigDecimal paidOutKzt,
    BigDecimal deficitKzt,
    boolean reconciliationPending,
    String currency,
    boolean simulated
) {
    public static PayoutBalanceView from(OrganizerBalance balance) {
        return new PayoutBalanceView(
            balance.isPayoutsBlocked() ? BigDecimal.ZERO.setScale(2) : balance.getAvailable().amount(),
            balance.isPayoutsBlocked() ? balance.getAvailable().amount() : BigDecimal.ZERO.setScale(2),
            balance.getPendingPayout().amount(),
            balance.getPaidOut().amount(),
            balance.getDebt().amount(),
            balance.isPayoutsBlocked(),
            "KZT",
            true
        );
    }
}
