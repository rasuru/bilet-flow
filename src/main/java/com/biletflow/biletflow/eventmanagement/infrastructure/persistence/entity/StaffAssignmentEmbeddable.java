package com.biletflow.biletflow.eventmanagement.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.UUID;

@Embeddable
public class StaffAssignmentEmbeddable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    public StaffAssignmentEmbeddable() {}

    public StaffAssignmentEmbeddable(Long userId, String role, Instant assignedAt) {
        this.userId = userId;
        this.role = role;
        this.assignedAt = assignedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }
}
