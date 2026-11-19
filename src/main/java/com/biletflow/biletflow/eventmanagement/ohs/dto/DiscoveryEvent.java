package com.biletflow.biletflow.eventmanagement.ohs.dto;

import java.time.Instant;
import java.util.UUID;

public record DiscoveryEvent(
    UUID id,
    String title,
    String category,
    Instant startAt,
    String imageUrl,
    String venueName,
    String venueAddress,
    Instant registrationStartAt
) {}
