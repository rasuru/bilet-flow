package com.biletflow.biletflow.eventmanagement.application.socialevent.command;

import com.biletflow.biletflow.eventmanagement.domain.EventDateRange;
import com.biletflow.biletflow.eventmanagement.domain.RegistrationWindow;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import java.util.Objects;

public record DuplicateEventCommand(SocialEventId sourceEventId, EventDateRange newDateRange, RegistrationWindow newRegistrationWindow) {
    public DuplicateEventCommand {
        Objects.requireNonNull(sourceEventId, "Source SocialEventId cannot be null");
        Objects.requireNonNull(newDateRange, "New date range cannot be null");
        Objects.requireNonNull(newRegistrationWindow, "New registration window cannot be null");
    }
}
