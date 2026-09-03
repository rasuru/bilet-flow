package com.biletflow.biletflow.eventmanagement.application.dto;

import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import com.biletflow.biletflow.eventmanagement.domain.StaffRole;
import java.util.Objects;

public record AssignStaffCommand(SocialEventId eventId, Long staffUserId, StaffRole role) {
    public AssignStaffCommand {
        Objects.requireNonNull(eventId, "SocialEventId cannot be null");
        Objects.requireNonNull(staffUserId, "Staff User ID cannot be null");
        Objects.requireNonNull(role, "StaffRole cannot be null");
    }
}
