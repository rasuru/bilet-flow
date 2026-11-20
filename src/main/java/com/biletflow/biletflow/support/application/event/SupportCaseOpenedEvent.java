package com.biletflow.biletflow.support.application.event;

import java.time.Instant;
import java.util.UUID;

public record SupportCaseOpenedEvent(UUID caseId, Long requesterUserId, Instant occurredAt) {}
