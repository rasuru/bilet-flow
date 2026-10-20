package com.biletflow.biletflow.eventmanagement.application.cancellation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CancellationRecipients {
    List<String> attendees(UUID eventId);
    Optional<String> verifiedEmail(long userId);
}
