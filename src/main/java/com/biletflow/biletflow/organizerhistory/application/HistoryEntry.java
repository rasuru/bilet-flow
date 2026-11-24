package com.biletflow.biletflow.organizerhistory.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoryEntry(
    UUID id,
    Instant timestamp,
    Long actorUserId,
    HistoryCategory category,
    String action,
    String entityType,
    UUID entityId,
    String description
) {
    public HistoryEntry {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(entityType, "entityType cannot be null");
        description = description == null ? "" : description;
    }
}
