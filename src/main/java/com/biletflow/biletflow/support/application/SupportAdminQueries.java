package com.biletflow.biletflow.support.application;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.support.domain.supportcase.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SupportAdminQueries {
    record Summary(
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
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant escalatedAt
    ) {
        static Summary from(SupportCase supportCase) {
            return new Summary(
                supportCase.getId().value(), supportCase.getType(), supportCase.getRequesterUserId(), supportCase.getSubject(),
                supportCase.getCategory(), supportCase.getEventId(), supportCase.getOrderId(), supportCase.getTicketId(),
                supportCase.getStatus(), supportCase.getAssignedSupportUserId(), supportCase.getCreatedAt(),
                supportCase.getUpdatedAt(), supportCase.getResolvedAt(), supportCase.getEscalatedAt()
            );
        }
    }

    record Message(UUID id, Long authorUserId, MessageAuthorType authorType, String content, Instant sentAt) {}

    record Detail(Summary supportCase, List<Message> messages) {}

    default List<Summary> search(CaseStatus status, UUID eventId, boolean escalatedOnly, QueryPage page) {
        return search(status, eventId, null, escalatedOnly, page);
    }

    List<Summary> search(CaseStatus status, UUID eventId, UUID orderId, boolean escalatedOnly, QueryPage page);
}
