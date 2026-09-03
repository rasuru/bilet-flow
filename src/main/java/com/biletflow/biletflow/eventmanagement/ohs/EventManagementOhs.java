package com.biletflow.biletflow.eventmanagement.ohs;

import com.biletflow.biletflow.eventmanagement.ohs.dto.EventSeatMapView;
import com.biletflow.biletflow.eventmanagement.ohs.dto.EventTicketingConfigurationView;
import java.util.UUID;

/**
 * Published synchronous contract of Event Management.
 */
public interface EventManagementOhs {
    /**
     * Narrow contract used by Ticket Inventory when it needs to initialize
     * EventInventory.
     */
    EventTicketingConfigurationView getTicketingConfiguration(UUID eventId);

    /**
     * Rich structural seat metadata for clients/read composition.
     * Transactional seat availability still belongs to Ticket Inventory.
     */
    EventSeatMapView getSeatMap(UUID eventId);
}
