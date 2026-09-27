package com.biletflow.biletflow.ordercheckout.application.order.command;

import java.util.Objects;
import java.util.UUID;

public record AddCheckoutItemCommand(
    UUID checkoutId,
    UUID ticketTypeId,
    String sessionId
    //CheckoutSelection selection
) {
    public AddCheckoutItemCommand {
        Objects.requireNonNull(checkoutId);
        Objects.requireNonNull(ticketTypeId);
        Objects.requireNonNull(sessionId);
        //Objects.requireNonNull(selection);

        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
    }
}
