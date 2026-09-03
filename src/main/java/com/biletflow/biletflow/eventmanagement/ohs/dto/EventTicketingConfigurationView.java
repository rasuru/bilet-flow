package com.biletflow.biletflow.eventmanagement.ohs.dto;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record EventTicketingConfigurationView(UUID eventId, SeatingModeView seatingMode, Set<TicketingSeatReferenceView> seats) {
    public EventTicketingConfigurationView {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(seatingMode, "seatingMode cannot be null");

        seats = seats == null ? Set.of() : Set.copyOf(seats);

        if (seats.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("seats cannot contain null");
        }

        if (seatingMode == SeatingModeView.GENERAL_ADMISSION && !seats.isEmpty()) {
            throw new IllegalArgumentException("General-admission configuration must not contain seats");
        }

        if (seatingMode == SeatingModeView.ASSIGNED_SEATING && seats.isEmpty()) {
            throw new IllegalArgumentException("Assigned-seating configuration requires seats");
        }
    }
}
