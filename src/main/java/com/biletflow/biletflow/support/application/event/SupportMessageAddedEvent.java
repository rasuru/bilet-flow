package com.biletflow.biletflow.support.application.event;

import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.UUID;

public record SupportMessageAddedEvent(
    UUID caseId,
    Long authorUserId,
    MessageAuthorType authorType,
    SupportCaseType caseType,
    UUID eventId,
    Long requesterUserId,
    Long assignedSupportUserId,
    Instant occurredAt
) {}
