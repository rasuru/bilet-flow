package com.biletflow.biletflow.support.persistence.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_case_audit")
public class SupportAuditJpaEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "support_case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "action", nullable = false, length = 64, updatable = false)
    private String action;

    @Column(name = "details", nullable = false, columnDefinition = "text", updatable = false)
    private String details;

    protected SupportAuditJpaEntity() {}

    public SupportAuditJpaEntity(UUID id, UUID caseId, UUID eventId, Instant occurredAt,
                                 Long actorUserId, String action, String details) {
        this.id = id;
        this.caseId = caseId;
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.action = action;
        this.details = details;
    }
}
