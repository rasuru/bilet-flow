package com.biletflow.biletflow.eventmanagement.ohs.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EventSeatMapView(UUID eventId, UUID venueLayoutId, List<SeatReferenceView> seats) {
    public EventSeatMapView {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(venueLayoutId, "venueLayoutId cannot be null");
        Objects.requireNonNull(seats, "seats cannot be null");

        seats = List.copyOf(seats);
    }
}
