package com.biletflow.biletflow.eventmanagement.application.socialevent.command;

import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import java.time.Instant;
import java.util.Objects;

public record CancelSocialEventCommand(SocialEventId eventId, Instant cancellationTime) {
    public CancelSocialEventCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
        Objects.requireNonNull(cancellationTime, "Cancellation time cannot be null");
    }
}
