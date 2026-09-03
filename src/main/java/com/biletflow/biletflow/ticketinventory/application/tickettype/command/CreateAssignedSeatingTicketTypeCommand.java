package com.biletflow.biletflow.ticketinventory.application.tickettype.command;

import com.biletflow.biletflow.common.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CreateAssignedSeatingTicketTypeCommand(
    UUID eventId,
    String name,
    String description,
    Money price,
    String priceCategory,
    int maxPerOrder,
    Instant salesStartAt,
    Instant salesEndAt
) {
    public CreateAssignedSeatingTicketTypeCommand {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        Objects.requireNonNull(priceCategory, "priceCategory cannot be null");
        Objects.requireNonNull(salesStartAt, "salesStartAt cannot be null");
        Objects.requireNonNull(salesEndAt, "salesEndAt cannot be null");

        priceCategory = priceCategory.trim();

        if (priceCategory.isEmpty()) {
            throw new IllegalArgumentException("priceCategory cannot be blank");
        }

        if (maxPerOrder <= 0) {
            throw new IllegalArgumentException("maxPerOrder must be greater than 0");
        }
    }
}
