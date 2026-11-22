package com.biletflow.biletflow.support.application.view;

import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.IssueCategory;
import com.biletflow.biletflow.support.domain.supportcase.SupportCase;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupportCaseView(
    UUID id,
    SupportCaseType type,
    Long requesterUserId,
    String subject,
    IssueCategory category,
    UUID eventId,
    UUID orderId,
    UUID ticketId,
    CaseStatus status,
    Long assignedSupportUserId,
    Instant assignedAt,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt,
    Instant escalatedAt,
    List<SupportMessageView> messages
) {
    public SupportCaseView {
        messages = List.copyOf(messages);
    }

    public static SupportCaseView from(SupportCase supportCase) {
        return new SupportCaseView(
            supportCase.getId().value(),
            supportCase.getType(),
            supportCase.getRequesterUserId(),
            supportCase.getSubject(),
            supportCase.getCategory(),
            supportCase.getEventId(),
            supportCase.getOrderId(),
            supportCase.getTicketId(),
            supportCase.getStatus(),
            supportCase.getAssignedSupportUserId(),
            supportCase.getAssignedAt(),
            supportCase.getCreatedAt(),
            supportCase.getUpdatedAt(),
            supportCase.getResolvedAt(),
            supportCase.getEscalatedAt(),
            supportCase.getMessages().stream().map(SupportMessageView::from).toList()
        );
    }
}
