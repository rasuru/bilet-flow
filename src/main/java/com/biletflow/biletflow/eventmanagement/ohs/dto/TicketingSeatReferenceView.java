package com.biletflow.biletflow.eventmanagement.ohs.dto;

import java.util.Objects;
import java.util.UUID;

public record TicketingSeatReferenceView(UUID seatId, String priceCategory) {
    public TicketingSeatReferenceView {
        Objects.requireNonNull(seatId, "seatId cannot be null");

        priceCategory = priceCategory == null ? "STANDARD" : priceCategory.trim();

        if (priceCategory.isEmpty()) {
            priceCategory = "STANDARD";
        }
    }
}
