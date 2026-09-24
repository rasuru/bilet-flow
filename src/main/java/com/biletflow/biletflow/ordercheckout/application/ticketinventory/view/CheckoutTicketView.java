package com.biletflow.biletflow.ordercheckout.application.ticketinventory.view;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CheckoutTicketView(
    UUID id,
    UUID ticketTypeId,
    UUID eventId,
    UUID orderId,
    String attendeeEmail,
    Long ownerUserId,
    UUID seatId,
    UUID ticketCode,
    TicketStatus status,
    Instant issuedAt
) {
    public CheckoutTicketView {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(attendeeEmail, "attendeeEmail cannot be null");
        Objects.requireNonNull(ticketCode, "ticketCode cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(issuedAt, "issuedAt cannot be null");
    }
}
