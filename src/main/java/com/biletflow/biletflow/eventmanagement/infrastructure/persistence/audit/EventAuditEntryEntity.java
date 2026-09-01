package com.biletflow.biletflow.eventmanagement.infrastructure.persistence.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_activity_log")
public class EventAuditEntryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "description", nullable = false)
    private String description;

    protected EventAuditEntryEntity() {}

    public EventAuditEntryEntity(UUID id, UUID eventId, Instant timestamp, Long actorUserId, String type, String description) {
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
