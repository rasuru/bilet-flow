package com.biletflow.biletflow.support.integration.audit;

import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.support.application.event.SupportCaseAssignedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Writes to the existing shared audit store in the same transaction as the support operation. */
@Component
public class SupportAuditEventHandler {
    private final FinancialAudit audit;
    public SupportAuditEventHandler(FinancialAudit audit) { this.audit = Objects.requireNonNull(audit); }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOpened(SupportCaseOpenedEvent event) {
        audit.record("SUPPORT", event.caseId(), event.requesterUserId(), "CASE_OPENED");
    }
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onMessage(SupportMessageAddedEvent event) {
        audit.record("SUPPORT", event.caseId(), event.authorUserId(), "MESSAGE_ADDED:" + event.authorType());
    }
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStatusChanged(SupportCaseStatusChangedEvent event) {
        audit.recordDetails("SUPPORT", event.caseId(), event.actorUserId(), "CASE_STATUS_CHANGED",
            event.previousStatus() + " -> " + event.newStatus());
    }
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAssigned(SupportCaseAssignedEvent event) {
        audit.recordDetails("SUPPORT", event.caseId(), event.actorUserId(), "CASE_ASSIGNED",
            "assignedSupportUserId=" + event.assignedSupportUserId());
    }
}
