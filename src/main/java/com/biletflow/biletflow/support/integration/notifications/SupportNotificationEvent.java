package com.biletflow.biletflow.support.integration.notifications;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseAssignedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stable, versioned integration payload for durable Support email notifications. */
public record SupportNotificationEvent(
    UUID caseId,
    Kind kind,
    SupportCaseType caseType,
    UUID eventId,
    Long requesterUserId,
    Long assignedSupportUserId,
    MessageAuthorType authorType,
    String status
) {
    public static final String TYPE = "support.notification.v1";

    public enum Kind { CASE_OPENED, CASE_ASSIGNED, STATUS_CHANGED, MESSAGE_ADDED }

    public IntegrationEvent toIntegrationEvent(Instant occurredAt) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("kind", kind.name());
        attributes.put("caseType", caseType.name());
        attributes.put("requesterUserId", requesterUserId.toString());
        attributes.put("occurredAt", occurredAt.toString());
        if (eventId != null) attributes.put("eventId", eventId.toString());
        if (assignedSupportUserId != null) attributes.put("assignedSupportUserId", assignedSupportUserId.toString());
        if (authorType != null) attributes.put("authorType", authorType.name());
        if (status != null) attributes.put("status", status);

        String identity = TYPE + ":" + caseId + ":" + kind + ":" + occurredAt;
        UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        return new IntegrationEvent(id, TYPE, caseId, attributes);
    }

    public static SupportNotificationEvent from(IntegrationEvent event) {
        if (!TYPE.equals(event.type())) throw new IllegalArgumentException("Unexpected event type: " + event.type());
        Map<String, String> attributes = event.attributes();
        return new SupportNotificationEvent(
            event.aggregateId(),
            Kind.valueOf(attributes.get("kind")),
            SupportCaseType.valueOf(attributes.get("caseType")),
            attributes.containsKey("eventId") ? UUID.fromString(attributes.get("eventId")) : null,
            Long.valueOf(attributes.get("requesterUserId")),
            attributes.containsKey("assignedSupportUserId") ? Long.valueOf(attributes.get("assignedSupportUserId")) : null,
            attributes.containsKey("authorType") ? MessageAuthorType.valueOf(attributes.get("authorType")) : null,
            attributes.get("status")
        );
    }

    public static SupportNotificationEvent opened(SupportCaseOpenedEvent event) {
        return new SupportNotificationEvent(event.caseId(), Kind.CASE_OPENED, event.caseType(), event.eventId(),
            event.requesterUserId(), null, null, null);
    }

    public static SupportNotificationEvent assigned(SupportCaseAssignedEvent event) {
        return new SupportNotificationEvent(event.caseId(), Kind.CASE_ASSIGNED, event.caseType(), event.eventId(),
            event.requesterUserId(), event.assignedSupportUserId(), null, null);
    }

    public static SupportNotificationEvent statusChanged(SupportCaseStatusChangedEvent event) {
        return new SupportNotificationEvent(event.caseId(), Kind.STATUS_CHANGED, event.caseType(), event.eventId(),
            event.requesterUserId(), null, null, event.newStatus().name());
    }

    public static SupportNotificationEvent messageAdded(SupportMessageAddedEvent event) {
        return new SupportNotificationEvent(event.caseId(), Kind.MESSAGE_ADDED, event.caseType(), event.eventId(),
            event.requesterUserId(), event.assignedSupportUserId(), event.authorType(), null);
    }
}
