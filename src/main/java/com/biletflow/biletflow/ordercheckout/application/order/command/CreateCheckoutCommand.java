package com.biletflow.biletflow.ordercheckout.application.order.command;

public record CreateCheckoutCommand(
    long ownerId,
    UUID eventId
) {
    public CreateCheckoutCommand {
        if (ownerId <= 0) {
            throw new IllegalArgumentException("ownerId must be positive");
        }

        Objects.requireNonNull(eventId, "eventId cannot be null");
    }
}
