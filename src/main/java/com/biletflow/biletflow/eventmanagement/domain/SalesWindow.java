package com.biletflow.biletflow.eventmanagement.domain;

import java.time.Instant;
import java.util.Objects;

public record SalesWindow(Instant start, Instant end) {
    public SalesWindow {
        Objects.requireNonNull(start, "Sales start time cannot be null");
        Objects.requireNonNull(end, "Sales end time cannot be null");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Sales end time cannot be before start time");
        }
    }

    public boolean isOpenAt(Instant now) {
        return !now.isBefore(start) && !now.isAfter(end);
    }
}
