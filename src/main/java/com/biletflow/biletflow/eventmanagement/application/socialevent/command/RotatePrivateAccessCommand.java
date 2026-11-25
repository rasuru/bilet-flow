package com.biletflow.biletflow.eventmanagement.application.socialevent.command;

import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import java.util.Objects;

public record RotatePrivateAccessCommand(SocialEventId eventId) {
    public RotatePrivateAccessCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
    }
}
