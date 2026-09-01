package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.domain.EventDateRange;
import com.biletflow.biletflow.eventmanagement.domain.RegistrationWindow;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DuplicateEventCommand(
    SocialEventId sourceEventId,
    Instant executionTime,
    EventDateRange newDateRange,
    RegistrationWindow newRegistrationWindow
) {
    public DuplicateEventCommand {
        Objects.requireNonNull(sourceEventId, "Source SocialEventId cannot be null");
        Objects.requireNonNull(executionTime, "Execution time cannot be null");
        Objects.requireNonNull(newDateRange, "New date range cannot be null");
        Objects.requireNonNull(newRegistrationWindow, "New registration window cannot be null");
    }
}
