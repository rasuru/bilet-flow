package com.biletflow.biletflow.promotions.domain.campaign;

import java.time.Instant;
import java.util.Objects;

public record ValidityPeriod(Instant startsAt, Instant endsAt) {
    public ValidityPeriod {
        Objects.requireNonNull(startsAt, "startsAt cannot be null");
        Objects.requireNonNull(endsAt, "endsAt cannot be null");

        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Validity period must end after it starts");
        }
    }

    public boolean hasNotStartedAt(Instant now) {
        return Objects.requireNonNull(now, "now cannot be null").isBefore(startsAt);
    }

    public boolean hasEndedAt(Instant now) {
        return !Objects.requireNonNull(now, "now cannot be null").isBefore(endsAt);
    }

    public boolean isActiveAt(Instant now) {
        return !hasNotStartedAt(now) && !hasEndedAt(now);
    }
}
