package com.biletflow.biletflow.paymentsmonetization.application.command;

import java.util.Objects;

public record RegisterPayoutAccountCommand(long organizerId, String holderName, String iban, boolean acceptSimulation) {
    public RegisterPayoutAccountCommand {
        Objects.requireNonNull(iban, "iban cannot be null");
        if (organizerId <= 0) throw new IllegalArgumentException("Invalid organizer");
        if (!acceptSimulation) throw new IllegalArgumentException(
            "Accept that payout-account verification is simulated; no bank checks the account"
        );
    }
}
