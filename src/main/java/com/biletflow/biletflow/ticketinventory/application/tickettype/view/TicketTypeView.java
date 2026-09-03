package com.biletflow.biletflow.ticketinventory.application.tickettype.view;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TicketTypeView(
    UUID id,
    UUID eventId,
    String name,
    String description,
    Money price,
    int maxPerOrder,
    Instant salesStartAt,
    Instant salesEndAt,
    TicketTypeStatus status,
    String priceCategory,
    Integer totalCapacity,
    Integer reserved,
    Integer sold,
    Integer available
) {
    public TicketTypeView {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        Objects.requireNonNull(salesStartAt, "salesStartAt cannot be null");
        Objects.requireNonNull(salesEndAt, "salesEndAt cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }
}
