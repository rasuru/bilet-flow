package com.biletflow.biletflow.ticketinventory.ohs;

import java.util.List;
import java.util.UUID;

public interface TicketAnalyticsOhs {
    Inventory inventory(UUID eventId, UUID ticketTypeId);
    List<Attendance> attendance(UUID eventId, UUID ticketTypeId);

    record TicketType(UUID id, String name, boolean free) {}

    record Inventory(List<TicketType> ticketTypes, long capacity, long sold, long remaining, long held, boolean sharedCapacity) {}

    record Attendance(UUID orderId, long registered, long checkedIn) {}
}
