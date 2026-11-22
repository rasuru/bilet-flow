package com.biletflow.biletflow.support.application.audit;

import java.time.Instant;
import java.util.UUID;

/** Support-owned audit port; it does not reuse the financial audit store. */
public interface SupportAuditRepository {
    void append(SupportAuditRecord record);

    record SupportAuditRecord(
        UUID id,
        UUID caseId,
        UUID eventId,
        Instant occurredAt,
        Long actorUserId,
        String action,
        String details
    ) {}
}
