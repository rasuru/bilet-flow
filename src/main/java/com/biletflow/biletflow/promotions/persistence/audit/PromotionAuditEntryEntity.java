package com.biletflow.biletflow.promotions.persistence.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promotions_activity_log")
public class PromotionAuditEntryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "type", nullable = false, updatable = false, length = 32)
    private String type;

    @Column(name = "description", nullable = false, updatable = false, length = 500)
    private String description;

    protected PromotionAuditEntryEntity() {}

    public PromotionAuditEntryEntity(UUID id, UUID eventId, Instant timestamp, Long actorUserId, String type, String description) {
        this.id = id;
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.actorUserId = actorUserId;
        this.type = type;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }
}
