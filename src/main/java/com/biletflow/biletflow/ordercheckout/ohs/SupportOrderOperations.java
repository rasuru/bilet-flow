package com.biletflow.biletflow.ordercheckout.ohs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Support-facing projections deliberately omit admission credentials. */
public interface SupportOrderOperations {
    record Ticket(UUID id, UUID ticketTypeId, UUID seatId, String attendeeEmail, String status) {}
    record Order(UUID id, UUID eventId, Long ownerUserId, String status, BigDecimal amountKzt, UUID paymentId,
                 String refundState, UUID refundId, Instant createdAt, List<Ticket> tickets) {
        public Order { tickets = List.copyOf(tickets); }
    }
    Order inspect(UUID eventId, UUID orderId);
    Order refund(UUID eventId, UUID orderId, boolean freeCancellation, String reason);
}

