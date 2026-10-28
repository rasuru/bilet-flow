package com.biletflow.biletflow.paymentsmonetization.application.command;

import java.util.Objects;
import java.util.UUID;

/** Administrator suspension of an event's paid sales */
public record SuspendPaidSalesCommand(UUID eventId, long actorId, String reason) {
    public SuspendPaidSalesCommand {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        if (reason == null || reason.isBlank() || reason.length() > 200) throw new IllegalArgumentException(
            "A reason of at most 200 characters is required"
        );
        reason = reason.strip();
    }
}
