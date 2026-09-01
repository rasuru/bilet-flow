package com.biletflow.biletflow.eventmanagement.domain;

import java.time.Instant;
import java.util.Objects;

public sealed interface RegistrationWindow {
    boolean isOpenAt(Instant now);

    record AlwaysOpen() implements RegistrationWindow {
        @Override
        public boolean isOpenAt(Instant now) {
            return true;
        }
    }

    record OpensAt(Instant start) implements RegistrationWindow {
        public OpensAt {
            Objects.requireNonNull(start, "Start instant cannot be null");
        }

        @Override
        public boolean isOpenAt(Instant now) {
            return !now.isBefore(start);
        }
    }

    record Bounded(Instant start, Instant end) implements RegistrationWindow {
        public Bounded {
            Objects.requireNonNull(start, "Start instant cannot be null");
            Objects.requireNonNull(end, "End instant cannot be null");
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("Registration end cannot be before start");
            }
        }

        @Override
        public boolean isOpenAt(Instant now) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
    }
}
