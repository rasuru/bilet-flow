package com.biletflow.biletflow.eventmanagement.ohs.dto;

import java.util.Objects;
import java.util.UUID;

public record SeatReferenceView(UUID seatId, String section, String row, String seatNumber, boolean accessible, String priceCategory) {
    public SeatReferenceView {
        Objects.requireNonNull(seatId, "seatId cannot be null");
        Objects.requireNonNull(section, "section cannot be null");
        Objects.requireNonNull(row, "row cannot be null");
        Objects.requireNonNull(seatNumber, "seatNumber cannot be null");
    }
}
