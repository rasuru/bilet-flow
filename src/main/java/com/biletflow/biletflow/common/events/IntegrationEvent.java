package com.biletflow.biletflow.common.events;

import java.util.Map;
import java.util.UUID;

/** Published integration facts contain identifiers only, never bank credentials or card data. */
public record IntegrationEvent(UUID id, String type, UUID aggregateId, Map<String, String> attributes) {
    public IntegrationEvent {
        attributes = Map.copyOf(attributes);
    }
}
