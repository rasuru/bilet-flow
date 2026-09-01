package com.biletflow.biletflow.eventmanagement.domain;

import java.util.Objects;

/**
 * Value Object representing a specific seat within a venue layout.
 */
public record SeatLocation(String section, String row, String seatNumber) {
    public SeatLocation {
        Objects.requireNonNull(section, "Section must not be null");
        Objects.requireNonNull(row, "Row must not be null");
        Objects.requireNonNull(seatNumber, "Seat number must not be null");

        if (section.isBlank()) {
            throw new IllegalArgumentException("Section cannot be empty");
        }
        if (row.isBlank()) {
            throw new IllegalArgumentException("Row cannot be empty");
        }
        if (seatNumber.isBlank()) {
            throw new IllegalArgumentException("Seat number cannot be empty");
        }

        // Normalize text attributes to maintain consistent value equality
        section = section.trim().toUpperCase();
        row = row.trim().toUpperCase();
        seatNumber = seatNumber.trim().toUpperCase();
    }

    /**
     * Helper to present the location in a readable standard format (e.g., "SEC A - ROW 12 - SEAT 4").
     */
    public String toLabel() {
        return String.format("SEC %s - ROW %s - SEAT %s", section, row, seatNumber);
    }
}
