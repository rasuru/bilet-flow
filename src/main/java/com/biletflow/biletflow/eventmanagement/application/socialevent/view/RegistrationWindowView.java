package com.biletflow.biletflow.eventmanagement.application.socialevent.view;

import java.time.Instant;

public record RegistrationWindowView(Mode mode, Instant startAt, Instant endAt) {
    public enum Mode {
        ALWAYS_OPEN,
        OPENS_AT,
        BOUNDED,
    }
}
