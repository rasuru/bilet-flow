package com.biletflow.biletflow.eventmanagement.application.query;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record VenueLayoutView(UUID layoutId, String name, List<VenueSeatView> seats) {
    public VenueLayoutView {
        Objects.requireNonNull(layoutId, "layoutId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(seats, "seats cannot be null");
        seats = List.copyOf(seats);
    }
}
