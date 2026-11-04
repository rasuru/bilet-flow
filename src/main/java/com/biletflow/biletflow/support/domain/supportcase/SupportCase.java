package com.biletflow.biletflow.support.domain.supportcase;

import com.biletflow.biletflow.support.domain.exceptions.InvalidSupportCaseStateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SupportCase {

    private final SupportCaseId id;
    private final SupportCaseType type;
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
        List<SupportMessage> messages
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.requesterUserId = requirePositiveUserId(requesterUserId, "requesterUserId");
        this.subject = requireSubject(subject);
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.eventId = eventId;
        this.orderId = orderId;
        this.ticketId = ticketId;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.assignedSupportUserId = assignedSupportUserId == null
            ? null
            : requirePositiveUserId(assignedSupportUserId, "assignedSupportUserId");
        this.assignedAt = assignedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.resolvedAt = resolvedAt;
        this.messages = new ArrayList<>(Objects.requireNonNull(messages, "messages cannot be null"));

        validateRehydratedState();
    }

    public static SupportCase open(
        SupportCaseType type,
        Long requesterUserId,
        String subject,
        IssueCategory category,
        String initialMessage,
        UUID eventId,
        UUID orderId,
        UUID ticketId,
        Instant now
    ) {
        Instant openedAt = Objects.requireNonNull(now, "now cannot be null");
        Long validRequesterId = requirePositiveUserId(requesterUserId, "requesterUserId");
        SupportMessage firstMessage = SupportMessage.create(
            validRequesterId,
            MessageAuthorType.REQUESTER,
            initialMessage,
            openedAt
        );

        return new SupportCase(
            SupportCaseId.generate(),
            type,
            validRequesterId,
            subject,
            category,
            eventId,
            orderId,
            ticketId,
            CaseStatus.OPEN,
            null,
            null,
            openedAt,
            openedAt,
            null,
            List.of(firstMessage)
        );
    }

    /** Reconstructs an aggregate from persisted state and validates its invariants. */
    public static SupportCase rehydrate(
        SupportCaseId id,
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
        List<SupportMessage> messages
    ) {
        return new SupportCase(
            id, type, requesterUserId, subject, category,
            eventId, orderId, ticketId, status,
            assignedSupportUserId, assignedAt, createdAt,
            updatedAt, resolvedAt, messages
        );
    }

    public void assignTo(Long supportUserId, Instant now) {
        ensureNotResolved();
        Long validSupportUserId = requirePositiveUserId(supportUserId, "supportUserId");

        // Repeating the same assignment is not a new domain change.
        if (Objects.equals(assignedSupportUserId, validSupportUserId)) {
            return;
        }

        Instant assignedTime = requireEventTime(now);
        this.assignedSupportUserId = validSupportUserId;
        this.assignedAt = assignedTime;
        this.updatedAt = assignedTime;
    }

    public void replyAsRequester(Long authorUserId, String content, Instant now) {
        ensureNotResolved();
        Long validAuthorId = requirePositiveUserId(authorUserId, "authorUserId");
        if (!requesterUserId.equals(validAuthorId)) {
            throw new InvalidSupportCaseStateException(
                "Only the requester may reply as the requester"
            );
        }
        appendMessage(validAuthorId, MessageAuthorType.REQUESTER, content, now);
    }

    public void replyAsSupportAgent(Long supportUserId, String content, Instant now) {
        ensureNotResolved();
        Long validSupportUserId = requirePositiveUserId(supportUserId, "supportUserId");
        appendMessage(validSupportUserId, MessageAuthorType.SUPPORT_AGENT, content, now);
    }

    private void appendMessage(
        Long authorUserId,
        MessageAuthorType authorType,
        String content,
        Instant now
    ) {
        Instant messageTime = requireEventTime(now);
        SupportMessage message = SupportMessage.create(authorUserId, authorType, content, messageTime);
        messages.add(message);
        updatedAt = messageTime;
    }

    /** Changes the lifecycle state according to the allowed transition graph. */
    public void changeStatus(CaseStatus newStatus, Instant now) {
        Objects.requireNonNull(newStatus, "newStatus cannot be null");
        ensureNotResolved();

        if (!isAllowedTransition(status, newStatus)) {
            throw new InvalidSupportCaseStateException(
                "Invalid support case status transition: " + status + " -> " + newStatus
            );
        }

        Instant changedAt = requireEventTime(now);
        this.status = newStatus;
        this.resolvedAt = newStatus == CaseStatus.RESOLVED ? changedAt : null;
        this.updatedAt = changedAt;
    }

    public void resolve(Instant now) {
        changeStatus(CaseStatus.RESOLVED, now);
    }

    private static boolean isAllowedTransition(CaseStatus current, CaseStatus target) {
        if (current == target) {
            return false;
        }

        return switch (current) {
            case OPEN -> target == CaseStatus.IN_PROGRESS
                || target == CaseStatus.WAITING_FOR_CUSTOMER
                || target == CaseStatus.RESOLVED;
            case IN_PROGRESS -> target == CaseStatus.WAITING_FOR_CUSTOMER
                || target == CaseStatus.RESOLVED;
            case WAITING_FOR_CUSTOMER -> target == CaseStatus.IN_PROGRESS
                || target == CaseStatus.RESOLVED;
            case RESOLVED -> false;
        };
    }

    private void ensureNotResolved() {
        if (status == CaseStatus.RESOLVED) {
            throw new InvalidSupportCaseStateException(
                "Resolved support cases cannot be modified"
            );
        }
    }

    private Instant requireEventTime(Instant now) {
        Instant eventTime = Objects.requireNonNull(now, "now cannot be null");
        if (eventTime.isBefore(updatedAt)) {
            throw new IllegalArgumentException("An operation timestamp cannot be before updatedAt");
        }
        return eventTime;
    }

    private void validateRehydratedState() {
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("A support case must contain its initial message");
        }

        for (SupportMessage message : messages) {
            Objects.requireNonNull(message, "messages cannot contain null");
        }

        SupportMessage initialMessage = messages.get(0); // Java 17 compatible.
        if (initialMessage.authorType() != MessageAuthorType.REQUESTER
            || !initialMessage.authorUserId().equals(requesterUserId)) {
            throw new IllegalArgumentException(
                "The initial message must be written by the requester"
            );
        }
        if (!initialMessage.sentAt().equals(createdAt)) {
            throw new IllegalArgumentException(
                "The initial message timestamp must match createdAt"
            );
        }

        Instant previousMessageTime = createdAt;
        for (SupportMessage message : messages) {
            if (message.sentAt().isBefore(previousMessageTime)
                || message.sentAt().isAfter(updatedAt)) {
                throw new IllegalArgumentException(
                    "Messages must be chronological and cannot be newer than updatedAt"
                );
            }
            if (message.authorType() == MessageAuthorType.REQUESTER
                && !message.authorUserId().equals(requesterUserId)) {
                throw new IllegalArgumentException(
                    "Requester messages must be written by the requester"
                );
            }
            previousMessageTime = message.sentAt();
        }

        if (status == CaseStatus.RESOLVED && resolvedAt == null) {
            throw new IllegalArgumentException("A resolved case must have resolvedAt");
        }
        if (status != CaseStatus.RESOLVED && resolvedAt != null) {
            throw new IllegalArgumentException(
                "Only a resolved case may have resolvedAt"
            );
        }

        if ((assignedSupportUserId == null) != (assignedAt == null)) {
            throw new IllegalArgumentException(
                "assignedSupportUserId and assignedAt must either both be present or both be absent"
            );
        }
        if (assignedAt != null
            && (assignedAt.isBefore(createdAt) || assignedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException(
                "assignedAt must be between createdAt and updatedAt"
            );
        }

        if (resolvedAt != null) {
            if (resolvedAt.isBefore(createdAt) || resolvedAt.isAfter(updatedAt)) {
                throw new IllegalArgumentException(
                    "resolvedAt must be between createdAt and updatedAt"
                );
            }
            if (resolvedAt.isBefore(previousMessageTime)) {
                throw new IllegalArgumentException(
                    "resolvedAt cannot be before the latest message"
                );
            }
            if (assignedAt != null && assignedAt.isAfter(resolvedAt)) {
                throw new IllegalArgumentException(
                    "assignedAt cannot be after resolvedAt"
                );
            }
        }
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

    public SupportCaseId getId() { return id; }
    public SupportCaseType getType() { return type; }
    public Long getRequesterUserId() { return requesterUserId; }
    public String getSubject() { return subject; }
    public IssueCategory getCategory() { return category; }
    public UUID getEventId() { return eventId; }
    public UUID getOrderId() { return orderId; }
    public UUID getTicketId() { return ticketId; }
    public CaseStatus getStatus() { return status; }
    public Long getAssignedSupportUserId() { return assignedSupportUserId; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public List<SupportMessage> getMessages() { return List.copyOf(messages); }

    public boolean isOwnedBy(Long userId) {
        return requesterUserId.equals(
            Objects.requireNonNull(userId, "userId cannot be null")
        );
    }
}
