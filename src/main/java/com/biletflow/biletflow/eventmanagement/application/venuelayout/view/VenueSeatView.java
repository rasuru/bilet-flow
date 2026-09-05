package com.biletflow.biletflow.eventmanagement.application.venuelayout.view;

import java.util.Objects;
import java.util.UUID;

public record VenueSeatView(UUID seatId, String section, String row, String seatNumber, boolean accessible, String priceCategory) {
    public VenueSeatView {
        Objects.requireNonNull(seatId, "seatId cannot be null");
        Objects.requireNonNull(section, "section cannot be null");
        Objects.requireNonNull(row, "row cannot be null");
        Objects.requireNonNull(seatNumber, "seatNumber cannot be null");
    }
}
