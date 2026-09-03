package com.biletflow.biletflow.ticketinventory.application.tickettype.command;

import com.biletflow.biletflow.common.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CreateGeneralAdmissionTicketTypeCommand(
    UUID eventId,
    String name,
    String description,
    Money price,
    int capacity,
    int maxPerOrder,
    Instant salesStartAt,
    Instant salesEndAt
) {
    public CreateGeneralAdmissionTicketTypeCommand {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        Objects.requireNonNull(salesStartAt, "salesStartAt cannot be null");
        Objects.requireNonNull(salesEndAt, "salesEndAt cannot be null");

        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than 0");
        }

        if (maxPerOrder <= 0) {
            throw new IllegalArgumentException("maxPerOrder must be greater than 0");
        }
    }
}
