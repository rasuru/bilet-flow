package com.biletflow.biletflow.paymentsmonetization.application.command;

import com.biletflow.biletflow.common.domain.Money;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record RequestPayoutCommand(long organizerId, UUID requestId, BigDecimal amountKzt, boolean acceptSimulation) {
    public RequestPayoutCommand {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        if (
            organizerId <= 0 ||
            amountKzt == null ||
            amountKzt.signum() <= 0 ||
            amountKzt.scale() > 2 ||
            amountKzt.setScale(2).precision() > 19
        ) throw new IllegalArgumentException("Payout amount must be positive KZT with at most two decimal places");
        if (!acceptSimulation) throw new IllegalArgumentException("Accept the simulated payout terms; no real money will move");
    }

    public Money amount() {
        return Money.kzt(amountKzt);
    }
}
