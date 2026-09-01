package com.biletflow.biletflow.eventmanagement.domain;

import java.util.Objects;
import java.util.UUID;

public record SocialEventId(UUID value) {
    public SocialEventId {
        Objects.requireNonNull(value, "SocialEventId cannot be null");
    }

    public static SocialEventId generate() {
        return new SocialEventId(UUID.randomUUID());
    }
}
