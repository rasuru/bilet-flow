package com.biletflow.biletflow.support.application.event;

import java.time.Instant;
import java.util.UUID;

public record SupportCaseAssignedEvent(
    UUID caseId, Long assignedSupportUserId, Long actorUserId, Instant occurredAt
) {}
