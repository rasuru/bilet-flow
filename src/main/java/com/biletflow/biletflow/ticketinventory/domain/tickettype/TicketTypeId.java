package com.biletflow.biletflow.ticketinventory.domain.tickettype;

import java.util.Objects;
import java.util.UUID;

public record TicketTypeId(UUID value) {
    public TicketTypeId {
        Objects.requireNonNull(value, "TicketTypeId cannot be null");
    }

    public static TicketTypeId generate() {
        return new TicketTypeId(UUID.randomUUID());
    }
}
