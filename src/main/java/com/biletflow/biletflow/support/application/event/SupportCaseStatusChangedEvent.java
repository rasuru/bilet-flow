package com.biletflow.biletflow.support.application.event;

import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.UUID;

public record SupportCaseStatusChangedEvent(
    UUID caseId,
    CaseStatus previousStatus,
    CaseStatus newStatus,
    Long actorUserId,
    SupportCaseType caseType,
    UUID eventId,
    Long requesterUserId,
    Instant occurredAt
) {}
