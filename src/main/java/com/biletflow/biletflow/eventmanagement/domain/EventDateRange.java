package com.biletflow.biletflow.eventmanagement.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record EventDateRange(Instant startAt, Instant endAt) {
    public EventDateRange {
        Objects.requireNonNull(startAt, "Start date cannot be null");
        Objects.requireNonNull(endAt, "End date cannot be null");

        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Event end date must be strictly after start date");
        }
    }

    public static EventDateRange createFuture(Instant startAt, Instant endAt, Instant now) {
        if (startAt.isBefore(now)) {
            throw new IllegalArgumentException("Event start date cannot be in the past");
        }

        return new EventDateRange(startAt, endAt);
    }

    public boolean hasStarted(Instant referenceTime) {
        return !referenceTime.isBefore(startAt);
    }

    public boolean hasEnded(Instant referenceTime) {
        return referenceTime.isAfter(endAt);
    }

    public boolean isCurrentlyActive(Instant referenceTime) {
        return hasStarted(referenceTime) && !hasEnded(referenceTime);
    }

    public long durationInHours() {
        return Duration.between(startAt, endAt).toHours();
    }
}
