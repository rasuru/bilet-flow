package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

import java.util.Objects;
import java.util.UUID;

public record CheckoutIssueTicketItem(UUID ticketTypeId, String attendeeEmail, Long ownerUserId, UUID seatId) {
    public CheckoutIssueTicketItem {
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
        Objects.requireNonNull(attendeeEmail, "attendeeEmail cannot be null");

        if (attendeeEmail.isBlank()) {
            throw new IllegalArgumentException("attendeeEmail cannot be blank");
        }
    }
}
