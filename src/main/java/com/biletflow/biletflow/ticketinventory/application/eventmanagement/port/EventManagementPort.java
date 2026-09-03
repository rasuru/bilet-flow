package com.biletflow.biletflow.ticketinventory.application.eventmanagement.port;

import java.util.UUID;

/**
 * Outbound application port owned by Ticket Inventory.
 *
 * Infrastructure implements this port with an Anti-Corruption Layer that calls
 * Event Management's synchronous OHS and translates its published contract into
 * EventTicketingConfiguration.
 *
 * Ticket Inventory must not import Event Management domain objects.
 */
public interface EventManagementPort {
    EventTicketingConfiguration getTicketingConfiguration(UUID eventId);
}
