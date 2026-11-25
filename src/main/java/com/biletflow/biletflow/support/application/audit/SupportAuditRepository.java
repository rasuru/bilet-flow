package com.biletflow.biletflow.support.application.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Support-owned audit port; it does not reuse the financial audit store. */
public interface SupportAuditRepository {
    void append(SupportAuditRecord record);

    List<SupportAuditRecord> findByCaseId(UUID caseId);

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

