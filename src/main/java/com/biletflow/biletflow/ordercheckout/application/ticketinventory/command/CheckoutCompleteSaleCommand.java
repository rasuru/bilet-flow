package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.CheckoutHoldReference;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.CheckoutIssueTicketItem;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CheckoutCompleteSaleCommand(UUID orderId, UUID eventId, List<CheckoutHoldReference> holds, List<CheckoutIssueTicketItem> items) {
    public CheckoutCompleteSaleCommand {
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
