package com.biletflow.biletflow.eventmanagement.application.socialevent.command;

import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import java.util.Objects;

public record UnpublishEventCommand(SocialEventId eventId) {
    public UnpublishEventCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
    }
}
