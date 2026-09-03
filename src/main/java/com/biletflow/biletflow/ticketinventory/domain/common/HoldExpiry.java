package com.biletflow.biletflow.ticketinventory.domain.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record HoldExpiry(Duration duration) {
    public HoldExpiry {
        Objects.requireNonNull(duration, "Hold duration cannot be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Hold duration must be positive");
        }
    }

    public static HoldExpiry defaultExpiry() {
        return new HoldExpiry(Duration.ofMinutes(10));
    }

    public Instant calculateExpirationFrom(Instant startPoint) {
        Objects.requireNonNull(startPoint, "Start point cannot be null");
        return startPoint.plus(duration);
    }
}
