package com.biletflow.biletflow.support.integration.audit;

import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.support.application.audit.SupportAuditRepository;
import com.biletflow.biletflow.support.application.audit.SupportAuditRepository.SupportAuditRecord;
import com.biletflow.biletflow.support.application.event.SupportCaseAssignedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseEscalatedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Persists Support-owned audit rows and projects status changes to the Event activity timeline. */
@Component
public class SupportAuditEventHandler {
    private final SupportAuditRepository audit;
    private final EventManagementOhs eventManagement;

    public SupportAuditEventHandler(SupportAuditRepository audit, EventManagementOhs eventManagement) {
        this.audit = Objects.requireNonNull(audit);
        this.eventManagement = Objects.requireNonNull(eventManagement);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOpened(SupportCaseOpenedEvent event) {
        append(event.caseId(), event.eventId(), event.occurredAt(), event.requesterUserId(),
            "CASE_OPENED", "Support case opened: " + event.caseType());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onMessage(SupportMessageAddedEvent event) {
        append(event.caseId(), event.eventId(), event.occurredAt(), event.authorUserId(),
            "MESSAGE_ADDED", "Message added by " + event.authorType());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStatusChanged(SupportCaseStatusChangedEvent event) {
        append(event.caseId(), event.eventId(), event.occurredAt(), event.actorUserId(),
            "CASE_STATUS_CHANGED", event.previousStatus() + " -> " + event.newStatus());
        if (event.eventId() != null) {
            eventManagement.recordSupportCaseStatusChanged(
                event.eventId(), event.caseId(), event.actorUserId(),
                event.previousStatus().name(), event.newStatus().name(), event.occurredAt()
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAssigned(SupportCaseAssignedEvent event) {
        append(event.caseId(), event.eventId(), event.occurredAt(), event.actorUserId(),
            "CASE_ASSIGNED", "assignedSupportUserId=" + event.assignedSupportUserId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onEscalated(SupportCaseEscalatedEvent event) {
        append(event.caseId(), event.eventId(), event.occurredAt(), event.actorUserId(),
            "CASE_ESCALATED", "Support case escalated to platform administrators");
    }

    private void append(UUID caseId, UUID eventId, java.time.Instant occurredAt,
                        Long actorUserId, String action, String details) {
        audit.append(new SupportAuditRecord(
            UUID.randomUUID(), caseId, eventId, occurredAt, actorUserId, action, details
        ));
    }
}
