package com.biletflow.biletflow.support.domain.supportcase;

import com.biletflow.biletflow.support.domain.exceptions.InvalidSupportCaseStateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SupportCase {

    private final SupportCaseId id;
    private final Long requesterUserId;
    private final String subject;
    private final IssueCategory category;

    private final UUID eventId;
    private final UUID orderId;
    private final UUID ticketId;

    private CaseStatus status;
    private Long assignedSupportUserId;
    private Instant assignedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    private final List<SupportMessage> messages;

    private SupportCase(
        SupportCaseId id,
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
        List<SupportMessage> messages
    ) {
        this.id = Objects.requireNonNull(id, "SupportCase id cannot be null");
        this.requesterUserId = requirePositiveUserId(requesterUserId, "requesterUserId");
        this.subject = requireSubject(subject);
        this.category = Objects.requireNonNull(category, "Issue category cannot be null");
        this.eventId = eventId;
        this.orderId = orderId;
        this.ticketId = ticketId;
        this.status = Objects.requireNonNull(status, "Case status cannot be null");
        this.assignedSupportUserId = assignedSupportUserId == null
            ? null
            : requirePositiveUserId(assignedSupportUserId, "assignedSupportUserId");
        this.assignedAt = assignedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.resolvedAt = resolvedAt;
        this.messages = new ArrayList<>(Objects.requireNonNull(messages, "messages cannot be null"));

        if (this.messages.isEmpty()) {
            throw new IllegalArgumentException("A support case must contain its initial message");
        }
        if (this.updatedAt.isBefore(this.createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }

        SupportMessage initialMessage = Objects.requireNonNull(this.messages.getFirst(), "Support case messages cannot contain null");
        if (initialMessage.authorType() != MessageAuthorType.REQUESTER || !initialMessage.authorUserId().equals(this.requesterUserId)) {
            throw new IllegalArgumentException("The initial message must be written by the requester");
        }
        if (!initialMessage.sentAt().equals(this.createdAt)) {
            throw new IllegalArgumentException("The initial message timestamp must match createdAt");
        }

        Instant previousMessageTime = this.createdAt;
        for (SupportMessage message : this.messages) {
            Objects.requireNonNull(message, "Support case messages cannot contain null");
            if (message.sentAt().isBefore(previousMessageTime) || message.sentAt().isAfter(this.updatedAt)) {
                throw new IllegalArgumentException("Support case messages must be chronological and cannot be newer than updatedAt");
            }
            previousMessageTime = message.sentAt();
        }

        if (this.status == CaseStatus.RESOLVED && this.resolvedAt == null) {
            throw new IllegalArgumentException("A resolved support case must have resolvedAt");
        }
        if (this.status != CaseStatus.RESOLVED && this.resolvedAt != null) {
            throw new IllegalArgumentException("Only a resolved support case may have resolvedAt");
        }
        if ((this.assignedSupportUserId == null) != (this.assignedAt == null)) {
            throw new IllegalArgumentException("Assignee and assignedAt must either both be present or both be absent");
        }
        if (this.assignedAt != null && (this.assignedAt.isBefore(this.createdAt) || this.assignedAt.isAfter(this.updatedAt))) {
            throw new IllegalArgumentException("assignedAt must be between createdAt and updatedAt");
        }
        if (this.resolvedAt != null && (!this.resolvedAt.equals(this.updatedAt) || this.resolvedAt.isBefore(this.createdAt))) {
            throw new IllegalArgumentException("resolvedAt must equal updatedAt and cannot be before createdAt");
        }
    }

    public static SupportCase open(
        Long requesterUserId,
        String subject,
        IssueCategory category,
        String initialMessage,
        UUID eventId,
        UUID orderId,
        UUID ticketId,
        Instant now
    ) {
        Objects.requireNonNull(now, "now cannot be null");
        Long validRequesterId = requirePositiveUserId(requesterUserId, "requesterUserId");
        SupportMessage firstMessage = SupportMessage.create(validRequesterId, MessageAuthorType.REQUESTER, initialMessage, now);

        return new SupportCase(
            SupportCaseId.generate(),
            validRequesterId,
            subject,
            category,
            eventId,
            orderId,
            ticketId,
            CaseStatus.OPEN,
            null,
            null,
            now,
            now,
            null,
            List.of(firstMessage)
        );
    }

    public static SupportCase rehydrate(
        SupportCaseId id,
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
        List<SupportMessage> messages
    ) {
        return new SupportCase(
            id,
            requesterUserId,
            subject,
            category,
            eventId,
            orderId,
            ticketId,
            status,
            assignedSupportUserId,
            assignedAt,
            createdAt,
            updatedAt,
            resolvedAt,
            messages
        );
    }

    public void assignTo(Long supportUserId, Instant now) {
        ensureNotResolved();
        Long validSupportUserId = requirePositiveUserId(supportUserId, "supportUserId");
        Instant assignedTime = requireEventTime(now);

        this.assignedSupportUserId = validSupportUserId;
        this.assignedAt = assignedTime;
        if (status == CaseStatus.OPEN) {
            status = CaseStatus.IN_PROGRESS;
        }
        updatedAt = assignedTime;
    }

    public void reply(Long authorUserId, MessageAuthorType authorType, String content, Instant now) {
        ensureNotResolved();
        Long validAuthorId = requirePositiveUserId(authorUserId, "authorUserId");
        Objects.requireNonNull(authorType, "authorType cannot be null");
        Instant replyTime = requireEventTime(now);

        if (authorType == MessageAuthorType.REQUESTER && !requesterUserId.equals(validAuthorId)) {
            throw new InvalidSupportCaseStateException("Only the requester may reply as the requester");
        }

        SupportMessage message = SupportMessage.create(validAuthorId, authorType, content, replyTime);
        messages.add(message);

        if (authorType == MessageAuthorType.SUPPORT_AGENT) {
            status = CaseStatus.WAITING_FOR_CUSTOMER;
        } else if (status == CaseStatus.WAITING_FOR_CUSTOMER) {
            status = CaseStatus.IN_PROGRESS;
        }

        updatedAt = replyTime;
    }

    public void resolve(Instant now) {
        ensureNotResolved();
        Instant resolvedTime = requireEventTime(now);
        this.status = CaseStatus.RESOLVED;
        this.resolvedAt = resolvedTime;
        this.updatedAt = resolvedTime;
    }

    private void ensureNotResolved() {
        if (status == CaseStatus.RESOLVED) {
            throw new InvalidSupportCaseStateException("Resolved support cases cannot be modified");
        }
    }

    private Instant requireEventTime(Instant now) {
        Instant eventTime = Objects.requireNonNull(now, "now cannot be null");
        if (eventTime.isBefore(updatedAt)) {
            throw new IllegalArgumentException("An operation timestamp cannot be before updatedAt");
        }
        return eventTime;
    }

    private static Long requirePositiveUserId(Long userId, String fieldName) {
        Objects.requireNonNull(userId, fieldName + " cannot be null");
        if (userId <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return userId;
    }

    private static String requireSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Support case subject cannot be empty");
        }
        return subject.trim();
    }

    public SupportCaseId getId() {
        return id;
    }

    public Long getRequesterUserId() {
        return requesterUserId;
    }

    public String getSubject() {
        return subject;
    }

    public IssueCategory getCategory() {
        return category;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public Long getAssignedSupportUserId() {
        return assignedSupportUserId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public List<SupportMessage> getMessages() {
        return List.copyOf(messages);
    }

    public boolean isOwnedBy(Long userId) {
        return requesterUserId.equals(Objects.requireNonNull(userId, "userId cannot be null"));
    }
}
