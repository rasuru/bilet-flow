package com.biletflow.biletflow.paymentsmonetization.application.view;

import com.biletflow.biletflow.paymentsmonetization.domain.payout.Payout;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutView(
    UUID id,
    BigDecimal amountKzt,
    String currency,
    String status,
    Instant requestedAt,
    Instant completedAt,
    String providerReference,
    String failureReason,
    boolean simulated
) {
    public static PayoutView from(Payout payout) {
        return new PayoutView(
            payout.getId().value(),
            payout.getAmount().amount(),
            "KZT",
            payout.getStatus().name(),
            payout.getRequestedAt(),
            payout.getCompletedAt(),
            payout.getProviderReference(),
            payout.getFailureReason(),
            true
        );
    }
}
