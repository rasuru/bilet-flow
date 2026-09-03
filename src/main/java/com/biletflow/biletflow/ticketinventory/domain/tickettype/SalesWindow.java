package com.biletflow.biletflow.ticketinventory.domain.tickettype;

import java.time.Instant;
import java.util.Objects;

public record SalesWindow(Instant startAt, Instant endAt) {
    public SalesWindow {
        Objects.requireNonNull(startAt, "startAt cannot be null");
        Objects.requireNonNull(endAt, "endAt cannot be null");

        if (endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("Sales window end time cannot be before start time");
        }
    }

    public boolean isOpenAt(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        return !now.isBefore(startAt) && !now.isAfter(endAt);
    }
}
