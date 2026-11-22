package com.biletflow.biletflow.support.application.event;

import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.UUID;

public record SupportCaseAssignedEvent(
    UUID caseId,
    Long assignedSupportUserId,
    Long actorUserId,
    SupportCaseType caseType,
    UUID eventId,
    Long requesterUserId,
    Instant occurredAt
) {}
