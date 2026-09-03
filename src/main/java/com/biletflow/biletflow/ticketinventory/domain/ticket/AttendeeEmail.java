package com.biletflow.biletflow.ticketinventory.domain.ticket;

import java.util.Locale;
import java.util.Objects;

public record AttendeeEmail(String value) {
    public AttendeeEmail {
        Objects.requireNonNull(value, "Attendee email cannot be null");

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Attendee email cannot be blank");
        }

        value = normalized;
    }
}
