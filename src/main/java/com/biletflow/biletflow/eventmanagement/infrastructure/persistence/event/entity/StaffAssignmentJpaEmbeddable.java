package com.biletflow.biletflow.eventmanagement.infrastructure.persistence.event.entity;

import com.biletflow.biletflow.eventmanagement.domain.StaffRole;
import jakarta.persistence.*;
import java.time.Instant;

@Embeddable
public class StaffAssignmentJpaEmbeddable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private StaffRole role;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected StaffAssignmentJpaEmbeddable() {}

    public StaffAssignmentJpaEmbeddable(Long userId, StaffRole role, Instant assignedAt) {
        this.userId = userId;
        this.role = role;
        this.assignedAt = assignedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public StaffRole getRole() {
        return role;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
