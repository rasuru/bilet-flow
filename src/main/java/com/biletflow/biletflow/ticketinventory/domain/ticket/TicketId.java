package com.biletflow.biletflow.ticketinventory.domain.ticket;

import java.util.Objects;
import java.util.UUID;

public record TicketId(UUID value) {
    public TicketId {
        Objects.requireNonNull(value, "TicketId cannot be null");
    }

    public static TicketId generate() {
        return new TicketId(UUID.randomUUID());
    }
}
