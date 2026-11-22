package com.biletflow.biletflow.support.integration.audit;

import static org.mockito.Mockito.*;

import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.support.application.audit.SupportAuditRepository;
import com.biletflow.biletflow.support.application.event.SupportCaseAssignedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportAuditEventHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private SupportAuditRepository audit;
    private EventManagementOhs eventManagement;
    private SupportAuditEventHandler handler;

    @BeforeEach
    void setUp() {
        audit = mock(SupportAuditRepository.class);
        eventManagement = mock(EventManagementOhs.class);
        handler = new SupportAuditEventHandler(audit, eventManagement);
    }

    @Test
    void recordsCaseOpenedWithEventContext() {
        handler.onOpened(new SupportCaseOpenedEvent(CASE_ID, 10L, SupportCaseType.ATTENDEE_SUPPORT, EVENT_ID, NOW));
        verify(audit).append(argThat(record -> record.caseId().equals(CASE_ID)
            && EVENT_ID.equals(record.eventId()) && record.actorUserId().equals(10L)
            && record.action().equals("CASE_OPENED")));
        verifyNoInteractions(eventManagement);
    }

    @Test
    void recordsMessagesWithEventContext() {
        handler.onMessage(new SupportMessageAddedEvent(CASE_ID, 20L, MessageAuthorType.SUPPORT_AGENT,
            SupportCaseType.ATTENDEE_SUPPORT, EVENT_ID, 10L, 20L, NOW));
        verify(audit).append(argThat(record -> EVENT_ID.equals(record.eventId())
            && record.actorUserId().equals(20L) && record.action().equals("MESSAGE_ADDED")));
    }

    @Test
    void recordsStatusAndProjectsItIntoEventActivityTimeline() {
        handler.onStatusChanged(new SupportCaseStatusChangedEvent(
            CASE_ID, CaseStatus.OPEN, CaseStatus.IN_PROGRESS, 20L, SupportCaseType.ATTENDEE_SUPPORT,
            EVENT_ID, 10L, NOW));

        verify(audit).append(argThat(record -> EVENT_ID.equals(record.eventId())
            && record.action().equals("CASE_STATUS_CHANGED") && record.details().contains("OPEN -> IN_PROGRESS")));
        verify(eventManagement).recordSupportCaseStatusChanged(
            EVENT_ID, CASE_ID, 20L, "OPEN", "IN_PROGRESS", NOW);
    }

    @Test
    void platformStatusChangeIsNotProjectedToEventTimeline() {
        handler.onStatusChanged(new SupportCaseStatusChangedEvent(
            CASE_ID, CaseStatus.OPEN, CaseStatus.IN_PROGRESS, 20L, SupportCaseType.PLATFORM_SUPPORT,
            null, 10L, NOW));

        verify(audit).append(any(SupportAuditRepository.SupportAuditRecord.class));
        verifyNoInteractions(eventManagement);
    }

    @Test
    void recordsAssignments() {
        handler.onAssigned(new SupportCaseAssignedEvent(CASE_ID, 30L, 20L,
            SupportCaseType.ATTENDEE_SUPPORT, EVENT_ID, 10L, NOW));
        verify(audit).append(argThat(record -> EVENT_ID.equals(record.eventId())
            && record.actorUserId().equals(20L) && record.details().contains("assignedSupportUserId=30")));
    }
}
