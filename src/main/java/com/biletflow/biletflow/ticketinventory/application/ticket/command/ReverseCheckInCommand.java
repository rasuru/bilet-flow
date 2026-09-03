package com.biletflow.biletflow.ticketinventory.application.ticket.command;

import java.util.Objects;
import java.util.UUID;

public record ReverseCheckInCommand(UUID ticketCode, UUID expectedEventId) {
    public ReverseCheckInCommand {
        Objects.requireNonNull(ticketCode, "ticketCode cannot be null");
        Objects.requireNonNull(expectedEventId, "expectedEventId cannot be null");
    }
}
