package com.biletflow.biletflow.ticketinventory.application.eventinventory.view;

import com.biletflow.biletflow.ticketinventory.application.common.InventoryKind;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EventInventoryView(
    UUID inventoryId,
    UUID eventId,
    InventoryKind inventoryKind,
    List<GaAvailabilityView> gaAvailability,
    List<SeatAvailabilityView> seatAvailability
) {
    public EventInventoryView {
        Objects.requireNonNull(inventoryId, "inventoryId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(inventoryKind, "inventoryKind cannot be null");
        gaAvailability = gaAvailability == null ? List.of() : List.copyOf(gaAvailability);
        seatAvailability = seatAvailability == null ? List.of() : List.copyOf(seatAvailability);
    }
}
