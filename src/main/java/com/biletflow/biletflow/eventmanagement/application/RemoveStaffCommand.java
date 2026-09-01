package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import java.util.Objects;
import java.util.UUID;

public record RemoveStaffCommand(SocialEventId eventId, Long staffUserId) {
    public RemoveStaffCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
        Objects.requireNonNull(staffUserId, "Staff User ID cannot be null");
    }
}
