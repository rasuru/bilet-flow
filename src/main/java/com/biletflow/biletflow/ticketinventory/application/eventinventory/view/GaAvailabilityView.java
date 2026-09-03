package com.biletflow.biletflow.ticketinventory.application.eventinventory.view;

import java.util.Objects;
import java.util.UUID;

public record GaAvailabilityView(UUID ticketTypeId, int totalCapacity, int reserved, int sold, int available) {
    public GaAvailabilityView {
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
    }
}
