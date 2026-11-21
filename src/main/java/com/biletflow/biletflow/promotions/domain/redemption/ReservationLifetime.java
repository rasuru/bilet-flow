package com.biletflow.biletflow.promotions.domain.redemption;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ReservationLifetime(Duration duration) {
    public ReservationLifetime {
        Objects.requireNonNull(duration, "Reservation lifetime cannot be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Reservation lifetime must be positive");
        }
    }

    public static ReservationLifetime defaultLifetime() {
        return new ReservationLifetime(Duration.ofMinutes(15));
    }

    public Instant expiresAtFrom(Instant now) {
        return Objects.requireNonNull(now, "now cannot be null").plus(duration);
    }
}
