package com.biletflow.biletflow.ticketinventory.application.tickettype.command;

import com.biletflow.biletflow.common.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UpdateAssignedSeatingTicketTypeCommand(
    UUID ticketTypeId,
    String name,
    String description,
    Money price,
    int maxPerOrder,
    Instant salesStartAt,
    Instant salesEndAt
) {
    public UpdateAssignedSeatingTicketTypeCommand {
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        Objects.requireNonNull(salesStartAt, "salesStartAt cannot be null");
        Objects.requireNonNull(salesEndAt, "salesEndAt cannot be null");

        if (maxPerOrder <= 0) {
            throw new IllegalArgumentException("maxPerOrder must be greater than 0");
        }
    }
}
