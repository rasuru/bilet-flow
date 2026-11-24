package com.biletflow.biletflow.ticketinventory.persistence.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_inventory_activity_log")
public class TicketInventoryAuditEntryEntity {

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

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "description", nullable = false, updatable = false, length = 500)
    private String description;

    protected TicketInventoryAuditEntryEntity() {}

    public TicketInventoryAuditEntryEntity(
        UUID id,
        UUID eventId,
        Instant timestamp,
        Long actorUserId,
        String type,
        UUID entityId,
        String description
    ) {
        this.id = id;
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.actorUserId = actorUserId;
        this.type = type;
        this.entityId = entityId;
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

    public UUID getEntityId() {
        return entityId;
    }

    public String getDescription() {
        return description;
    }
}
