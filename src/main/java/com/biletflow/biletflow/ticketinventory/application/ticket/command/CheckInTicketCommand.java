package com.biletflow.biletflow.ticketinventory.application.ticket.command;

import java.util.Objects;
import java.util.UUID;

public record CheckInTicketCommand(UUID ticketCode, UUID expectedEventId) {
    public CheckInTicketCommand {
        Objects.requireNonNull(ticketCode, "ticketCode cannot be null");
        Objects.requireNonNull(expectedEventId, "expectedEventId cannot be null");
    }
}
