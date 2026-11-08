package com.biletflow.biletflow.support.persistence.supportcase.entity;

import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.IssueCategory;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "support_case",
    indexes = {
        @Index(name = "idx_support_case_requester", columnList = "requester_user_id"),
        @Index(name = "idx_support_case_assignee", columnList = "assigned_support_user_id"),
        @Index(name = "idx_support_case_status", columnList = "status"),
        @Index(name = "idx_support_case_type_status", columnList = "case_type, status"),
        @Index(name = "idx_support_case_event", columnList = "event_id"),
        @Index(name = "idx_support_case_order", columnList = "order_id"),
        @Index(name = "idx_support_case_ticket", columnList = "ticket_id"),
    }
)
public class SupportCaseJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, length = 32, updatable = false)
    private SupportCaseType type;

    @Column(name = "requester_user_id", nullable = false, updatable = false)
    private Long requesterUserId;

    @Column(name = "subject", nullable = false, length = 255, updatable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32, updatable = false)
    private IssueCategory category;

    // These are identifiers owned by other bounded contexts, not JPA relationships.
    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "ticket_id", updatable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CaseStatus status;

    @Column(name = "assigned_support_user_id")
    private Long assignedSupportUserId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "support_case_message",
        joinColumns = @JoinColumn(name = "support_case_id"),
        indexes = @Index(name = "idx_support_case_message_case", columnList = "support_case_id")
    )
    @OrderColumn(name = "message_order")
    private List<SupportMessageJpaEmbeddable> messages = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SupportCaseJpaEntity() {}

    public SupportCaseJpaEntity(UUID id, SupportCaseType type, Long requesterUserId, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.requesterUserId = requesterUserId;
        this.createdAt = createdAt;
    }

    public void setMutableState(
        String subject,
        IssueCategory category,
        UUID eventId,
        UUID orderId,
        UUID ticketId,
        CaseStatus status,
        Long assignedSupportUserId,
        Instant assignedAt,
        Instant updatedAt,
        Instant resolvedAt
    ) {
        this.subject = subject;
        this.category = category;
        this.eventId = eventId;
        this.orderId = orderId;
        this.ticketId = ticketId;
        this.status = status;
        this.assignedSupportUserId = assignedSupportUserId;
        this.assignedAt = assignedAt;
        this.updatedAt = updatedAt;
        this.resolvedAt = resolvedAt;
    }

    public void replaceMessages(List<SupportMessageJpaEmbeddable> messages) {
        this.messages.clear();
        this.messages.addAll(messages);
    }

    public UUID getId() { return id; }
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
    public List<SupportMessageJpaEmbeddable> getMessages() { return messages; }
    public long getVersion() { return version; }
}
