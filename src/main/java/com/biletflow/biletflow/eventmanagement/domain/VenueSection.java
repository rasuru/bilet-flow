package com.biletflow.biletflow.eventmanagement.domain;

import java.util.List;
import java.util.Objects;

public record VenueSection(String name, List<VenueRow> rows) {
    public VenueSection {
        Objects.requireNonNull(name, "Section name cannot be null");
        Objects.requireNonNull(rows, "Rows cannot be null");

        name = name.trim().toUpperCase();

        if (name.isBlank()) {
            throw new IllegalArgumentException("Section name cannot be blank");
        }

        rows = List.copyOf(rows);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Venue section must contain at least one row");
        }

        if (rows.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Venue section cannot contain null rows");
        }
    }
}
