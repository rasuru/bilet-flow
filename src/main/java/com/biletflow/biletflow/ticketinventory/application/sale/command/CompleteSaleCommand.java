package com.biletflow.biletflow.ticketinventory.application.sale.command;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldReference;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.IssueTicketItem;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CompleteSaleCommand(UUID orderId, UUID eventId, List<HoldReference> holds, List<IssueTicketItem> items) {
    public CompleteSaleCommand {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(holds, "holds cannot be null");
        Objects.requireNonNull(items, "items cannot be null");

        holds = List.copyOf(holds);
        items = List.copyOf(items);

        if (holds.isEmpty()) {
            throw new IllegalArgumentException("At least one hold is required");
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException("At least one ticket item is required");
        }
    }
}
