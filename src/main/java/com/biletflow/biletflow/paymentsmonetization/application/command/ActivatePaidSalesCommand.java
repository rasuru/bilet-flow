package com.biletflow.biletflow.paymentsmonetization.application.command;

import java.util.Objects;
import java.util.UUID;

public record ActivatePaidSalesCommand(UUID eventId, long organizerId, boolean acceptedTerms, boolean simulatedVerification) {
    public ActivatePaidSalesCommand {
        Objects.requireNonNull(eventId, "eventId cannot be null");
    }
}
