package com.biletflow.biletflow.support.integration.notifications;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.support.domain.supportcase.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportNotificationEventHandlerTest {
    private IamOhs iam;
    private EmailSender sender;
    private SupportCaseRepository cases;
    private SupportNotificationEventHandler handler;
    private final Instant now = Instant.parse("2026-11-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        iam = mock(IamOhs.class);
        sender = mock(EmailSender.class);
        cases = mock(SupportCaseRepository.class);
        handler = new SupportNotificationEventHandler(iam, sender, cases);
        when(iam.findVerifiedUserEmail(any())).thenAnswer(invocation ->
            Optional.of(new VerifiedUserEmailView(invocation.getArgument(0), "person@example.com")));
    }

    @Test
    void exposesStableEventType() {
        org.junit.jupiter.api.Assertions.assertEquals(SupportNotificationEvent.TYPE, handler.eventType());
    }

    @Test
    void caseOpenedNotifiesRequester() {
        UUID caseId = UUID.randomUUID();
        handler.handle(event(caseId, Map.of("kind", "CASE_OPENED", "requesterUserId", "10", "occurredAt", now.toString())));

        verify(iam).findVerifiedUserEmail(10L);
        verify(sender).send(any(EmailSender.Email.class));
    }

    @Test
    void agentReplyNotifiesRequester() {
        SupportCase supportCase = newCaseWithAgent();
        when(cases.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        handler.handle(event(supportCase.getId().value(), Map.of(
            "kind", "MESSAGE_ADDED", "authorType", "SUPPORT_AGENT", "occurredAt", now.toString())));

        verify(iam).findVerifiedUserEmail(10L);
        verify(sender).send(any(EmailSender.Email.class));
    }

    @Test
    void requesterReplyNotifiesAssignedAgent() {
        SupportCase supportCase = newCaseWithAgent();
        when(cases.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        handler.handle(event(supportCase.getId().value(), Map.of(
            "kind", "MESSAGE_ADDED", "authorType", "REQUESTER", "occurredAt", now.toString())));

        verify(iam).findVerifiedUserEmail(20L);
        verify(sender).send(any(EmailSender.Email.class));
    }

    @Test
    void missingAssignedAgentDoesNotSendRequesterReplyNotification() {
        SupportCase supportCase = SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, 10L,
            "Issue", IssueCategory.OTHER, "Initial", null, null, null, now);
        when(cases.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        handler.handle(event(supportCase.getId().value(), Map.of(
            "kind", "MESSAGE_ADDED", "authorType", "REQUESTER", "occurredAt", now.toString())));

        verifyNoInteractions(iam, sender);
    }

    @Test
    void emailFailurePropagatesSoOutboxCanRetry() {
        when(iam.findVerifiedUserEmail(10L)).thenThrow(new IllegalStateException("IAM unavailable"));

        assertThrows(IllegalStateException.class, () -> handler.handle(event(UUID.randomUUID(), Map.of(
            "kind", "CASE_OPENED", "requesterUserId", "10", "occurredAt", now.toString()))));
        verify(sender, never()).send(any());
    }

    private SupportCase newCaseWithAgent() {
        SupportCase supportCase = SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, 10L,
            "Issue", IssueCategory.OTHER, "Initial", null, null, null, now);
        supportCase.assignTo(20L, now.plusSeconds(1));
        return supportCase;
    }

    private IntegrationEvent event(UUID caseId, Map<String, String> attributes) {
        return new IntegrationEvent(UUID.randomUUID(), SupportNotificationEvent.TYPE, caseId, attributes);
    }
}
