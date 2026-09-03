package com.biletflow.biletflow.eventmanagement.domain;

import java.time.Instant;
import java.util.Objects;

public record StaffAssignment(Long userId, StaffRole role, Instant assignedAt) {
    public StaffAssignment {
        Objects.requireNonNull(userId, "UserId cannot be null");
        Objects.requireNonNull(role, "Role cannot be null");
        Objects.requireNonNull(assignedAt, "AssignedAt timestamp cannot be null");
    }

    public static StaffAssignment create(Long userId, StaffRole role, Instant now) {
        return new StaffAssignment(userId, role, Objects.requireNonNull(now, "now cannot be null"));
    }
}
