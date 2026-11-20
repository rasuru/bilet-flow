package com.biletflow.biletflow.support.integration.notifications;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Stable, versioned integration payload for durable Support email notifications. */
public record SupportNotificationEvent(
    UUID caseId,
    Kind kind,
    Long requesterUserId,
    String status,
    MessageAuthorType authorType
) {
    public static final String TYPE = "support.notification.v1";

    public enum Kind { CASE_OPENED, STATUS_CHANGED, MESSAGE_ADDED }

    public IntegrationEvent toIntegrationEvent(String occurredAt) {
        Map<String, String> attributes = switch (kind) {
            case CASE_OPENED -> Map.of("kind", kind.name(), "requesterUserId", requesterUserId.toString(), "occurredAt", occurredAt);
            case STATUS_CHANGED -> Map.of("kind", kind.name(), "status", status, "occurredAt", occurredAt);
            case MESSAGE_ADDED -> Map.of("kind", kind.name(), "authorType", authorType.name(), "occurredAt", occurredAt);
        };
        String identity = TYPE + ":" + caseId + ":" + kind + ":" + occurredAt;
        UUID eventId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        return new IntegrationEvent(eventId, TYPE, caseId, attributes);
    }

    public static SupportNotificationEvent from(IntegrationEvent event) {
        if (!TYPE.equals(event.type())) throw new IllegalArgumentException("Unexpected event type: " + event.type());
        Map<String, String> a = event.attributes();
        Kind kind = Kind.valueOf(a.get("kind"));
        return switch (kind) {
            case CASE_OPENED -> new SupportNotificationEvent(event.aggregateId(), kind,
                Long.valueOf(a.get("requesterUserId")), null, null);
            case STATUS_CHANGED -> new SupportNotificationEvent(event.aggregateId(), kind, null, a.get("status"), null);
            case MESSAGE_ADDED -> new SupportNotificationEvent(event.aggregateId(), kind, null, null,
                MessageAuthorType.valueOf(a.get("authorType")));
        };
    }

    public static SupportNotificationEvent opened(SupportCaseOpenedEvent event) {
        return new SupportNotificationEvent(event.caseId(), Kind.CASE_OPENED, event.requesterUserId(), null, null);
    }
    public static SupportNotificationEvent statusChanged(SupportCaseStatusChangedEvent event) {
        return new SupportNotificationEvent(event.caseId(), Kind.STATUS_CHANGED, null, event.newStatus().name(), null);
    }
    public static SupportNotificationEvent messageAdded(SupportMessageAddedEvent event) {
        return new SupportNotificationEvent(event.caseId(), Kind.MESSAGE_ADDED, null, null, event.authorType());
    }
}
