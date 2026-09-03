package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

import java.util.Optional;
import java.util.UUID;

public interface EventInventoryRepository {
    EventInventory save(EventInventory eventInventory);

    Optional<EventInventory> findById(EventInventoryId id);

    Optional<EventInventory> findByEventId(UUID eventId);
}
