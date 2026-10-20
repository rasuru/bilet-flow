package com.biletflow.biletflow.eventmanagement.ohs;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable fact: cancellation has committed, not a request to refund orders. */
public record EventCancelled(UUID eventId, Instant cancelledAt) {
    public static final String TYPE = "eventmanagement.event-cancelled.v1";

    public EventCancelled {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(cancelledAt);
    }

    public IntegrationEvent toEvent() {
        var id = UUID.nameUUIDFromBytes((TYPE + ":" + eventId).getBytes(StandardCharsets.UTF_8));
        return new IntegrationEvent(id, TYPE, eventId, Map.of("cancelledAt", cancelledAt.toString()));
    }

    public static EventCancelled from(IntegrationEvent event) {
        if (!TYPE.equals(event.type())) throw new IllegalArgumentException("Unexpected cancellation event type");
        return new EventCancelled(event.aggregateId(), Instant.parse(event.attributes().get("cancelledAt")));
    }
}
