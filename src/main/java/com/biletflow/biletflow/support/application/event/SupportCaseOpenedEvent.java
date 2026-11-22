package com.biletflow.biletflow.support.application.event;

import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.UUID;

public record SupportCaseOpenedEvent(
    UUID caseId,
    Long requesterUserId,
    SupportCaseType caseType,
    UUID eventId,
    Instant occurredAt
) {}
