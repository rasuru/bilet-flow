package com.biletflow.biletflow.ticketinventory.ohs;

import java.util.UUID;

public interface SupportTicketOperations {
    void resend(UUID eventId, UUID orderId, UUID ticketId);
    void reverseCheckIn(UUID eventId, UUID orderId, UUID ticketId);
}

