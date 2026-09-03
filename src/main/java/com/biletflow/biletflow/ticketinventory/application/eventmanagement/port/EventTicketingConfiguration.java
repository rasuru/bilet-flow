package com.biletflow.biletflow.ticketinventory.application.eventmanagement.port;

import com.biletflow.biletflow.ticketinventory.application.common.InventoryKind;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record EventTicketingConfiguration(UUID eventId, InventoryKind inventoryKind, Map<UUID, String> seatPriceCategories) {
    public EventTicketingConfiguration {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(inventoryKind, "inventoryKind cannot be null");

        seatPriceCategories = seatPriceCategories == null ? Map.of() : Map.copyOf(seatPriceCategories);

        if (seatPriceCategories.keySet().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("seatPriceCategories cannot contain null seat ids");
        }

        if (inventoryKind == InventoryKind.GENERAL_ADMISSION && !seatPriceCategories.isEmpty()) {
            throw new IllegalArgumentException("General-admission configuration must not contain seats");
        }

        if (inventoryKind == InventoryKind.ASSIGNED_SEATING && seatPriceCategories.isEmpty()) {
            throw new IllegalArgumentException("Assigned-seating configuration requires seats");
        }
    }
}
