package com.biletflow.biletflow.support.ohs.dto;

import com.biletflow.biletflow.support.application.view.SupportCaseView;
import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.IssueCategory;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.UUID;

/** Stable, deliberately small published language for Support consumers. */
public record SupportCaseSummary(
    UUID caseId,
    SupportCaseType type,
    Long requesterUserId,
    String subject,
    IssueCategory category,
    UUID eventId,
    UUID orderId,
    UUID ticketId,
    CaseStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static SupportCaseSummary from(SupportCaseView view) {
        return new SupportCaseSummary(view.id(), view.type(), view.requesterUserId(), view.subject(),
            view.category(), view.eventId(), view.orderId(), view.ticketId(), view.status(),
            view.createdAt(), view.updatedAt());
    }
}
