package com.biletflow.biletflow.paymentsmonetization.application.view;

import com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount.PayoutAccount;
import java.time.Instant;

public record PayoutAccountView(
    String holderName,
    String maskedIban,
    String bankCode,
    String verification,
    Instant registeredAt,
    Instant updatedAt,
    boolean simulated
) {
    public static PayoutAccountView from(PayoutAccount account) {
        return new PayoutAccountView(
            account.getHolderName(),
            account.maskedIban(),
            account.getBankCode(),
            "SIMULATED",
            account.getRegisteredAt(),
            account.getUpdatedAt(),
            true
        );
    }
}
