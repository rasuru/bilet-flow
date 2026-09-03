package com.biletflow.biletflow.eventmanagement.domain;

import java.util.Objects;
import java.util.UUID;

public record VenueLayoutId(UUID value) {
    public VenueLayoutId {
        Objects.requireNonNull(value, "VenueLayoutId cannot be null");
    }

    public static VenueLayoutId generate() {
        return new VenueLayoutId(UUID.randomUUID());
    }
}
