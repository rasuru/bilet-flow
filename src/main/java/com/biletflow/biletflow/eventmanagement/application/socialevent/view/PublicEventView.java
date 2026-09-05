package com.biletflow.biletflow.eventmanagement.application.socialevent.view;

import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventStatus;
import java.time.Instant;
import java.util.UUID;

public record PublicEventView(
    UUID id,
    String title,
    String description,
    String category,
    String imageUrl,
    SocialEventStatus status,
    EventVisibility visibility,
    Instant startAt,
    Instant endAt,
    RegistrationWindowView registrationWindow,
    EventVenueView venue
) {}
