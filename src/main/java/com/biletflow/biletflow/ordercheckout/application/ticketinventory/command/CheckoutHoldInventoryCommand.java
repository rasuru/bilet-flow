package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

import java.util.Objects;
import java.util.UUID;

public record CheckoutHoldInventoryCommand(UUID eventId, UUID ticketTypeId, String sessionId, CheckoutHoldSelection selection) {
    public CheckoutHoldInventoryCommand {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(selection, "selection cannot be null");

        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
    }
}
