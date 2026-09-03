package com.biletflow.biletflow.eventmanagement.domain;

import java.util.List;
import java.util.Objects;

public record VenueRow(String label, List<VenueSeat> seats) {
    public VenueRow {
        Objects.requireNonNull(label, "Row label cannot be null");
        Objects.requireNonNull(seats, "Seats cannot be null");

        label = label.trim().toUpperCase();

        if (label.isBlank()) {
            throw new IllegalArgumentException("Row label cannot be blank");
        }

        seats = List.copyOf(seats);

        if (seats.isEmpty()) {
            throw new IllegalArgumentException("Venue row must contain at least one seat");
        }

        if (seats.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Venue row cannot contain null seats");
        }
    }
}
