package com.biletflow.biletflow.support.application.event;

import java.time.Instant;
import java.util.UUID;

public record SupportCaseEscalatedEvent(
    UUID caseId,
    Long actorUserId,
    Long requesterUserId,
    UUID eventId,
    Instant occurredAt
) {}
