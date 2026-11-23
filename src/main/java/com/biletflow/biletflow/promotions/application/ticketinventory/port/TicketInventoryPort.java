package com.biletflow.biletflow.promotions.application.ticketinventory.port;

import java.util.Set;
import java.util.UUID;

public interface TicketInventoryPort {
    Set<UUID> ticketTypeIdsOf(UUID eventId);
}
