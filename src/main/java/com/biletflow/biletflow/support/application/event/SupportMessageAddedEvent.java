package com.biletflow.biletflow.support.application.event;

import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import java.time.Instant;
import java.util.UUID;

public record SupportMessageAddedEvent(
    UUID caseId, Long authorUserId, MessageAuthorType authorType, Instant occurredAt
) {}
