package com.biletflow.biletflow.support.integration.audit;

import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.support.application.event.*;
import com.biletflow.biletflow.support.domain.supportcase.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportAuditEventHandlerTest {
    private FinancialAudit audit;
    private SupportAuditEventHandler handler;
    private final UUID caseId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        audit = mock(FinancialAudit.class);
        handler = new SupportAuditEventHandler(audit);
    }

    @Test
    void recordsCaseOpened() {
        handler.onOpened(new SupportCaseOpenedEvent(caseId, 10L, now));
        verify(audit).record("SUPPORT", caseId, 10L, "CASE_OPENED");
    }

    @Test
    void recordsMessageAddedWithAuthorType() {
        handler.onMessage(new SupportMessageAddedEvent(caseId, 20L, MessageAuthorType.SUPPORT_AGENT, now));
        verify(audit).record("SUPPORT", caseId, 20L, "MESSAGE_ADDED:SUPPORT_AGENT");
    }

    @Test
    void recordsStatusTransition() {
        handler.onStatusChanged(new SupportCaseStatusChangedEvent(
            caseId, CaseStatus.OPEN, CaseStatus.IN_PROGRESS, 20L, now));
        verify(audit).recordDetails("SUPPORT", caseId, 20L, "CASE_STATUS_CHANGED", "OPEN -> IN_PROGRESS");
    }

    @Test
    void recordsAssignment() {
        handler.onAssigned(new SupportCaseAssignedEvent(caseId, 30L, 20L, now));
        verify(audit).recordDetails("SUPPORT", caseId, 20L, "CASE_ASSIGNED", "assignedSupportUserId=30");
    }
}
