package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import com.biletflow.biletflow.eventmanagement.domain.Venue;
import java.util.Objects;
import java.util.UUID;

public record AttachVenueCommand(SocialEventId eventId, Venue venue) {
    public AttachVenueCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
        Objects.requireNonNull(venue, "Venue cannot be null");
    }
}
