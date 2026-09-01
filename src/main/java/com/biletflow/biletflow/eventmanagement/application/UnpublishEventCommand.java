package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import java.util.Objects;
import java.util.UUID;

public record UnpublishEventCommand(SocialEventId eventId) {
    public UnpublishEventCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
    }
}
