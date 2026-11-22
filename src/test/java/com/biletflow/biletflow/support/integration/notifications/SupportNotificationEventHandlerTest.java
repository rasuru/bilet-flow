package com.biletflow.biletflow.support.integration.notifications;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.support.application.event.SupportCaseAssignedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportNotificationEventHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private IamOhs iam;
    private EventManagementOhs eventManagement;
    private EmailSender sender;
    private SupportNotificationEventHandler handler;

    @BeforeEach
    void setUp() {
        iam = mock(IamOhs.class);
        eventManagement = mock(EventManagementOhs.class);
        sender = mock(EmailSender.class);
        handler = new SupportNotificationEventHandler(iam, eventManagement, sender);
        when(iam.findVerifiedUserEmail(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return Optional.of(new VerifiedUserEmailView(id, "user" + id + "@example.com"));
        });
        when(eventManagement.supportRouting(EVENT_ID)).thenReturn(Optional.of(
            new EventManagementOhs.SupportRouting(EVENT_ID, 20L, List.of(30L, 31L))));
    }

    @Test
    void exposesStableEventType() {
        org.junit.jupiter.api.Assertions.assertEquals(SupportNotificationEvent.TYPE, handler.eventType());
    }

    @Test
    void openedAttendeeCaseNotifiesRequesterOrganizerAndEventAdminStaff() {
        handler.handle(SupportNotificationEvent.opened(new SupportCaseOpenedEvent(
            CASE_ID, 10L, SupportCaseType.ATTENDEE_SUPPORT, EVENT_ID, NOW)).toIntegrationEvent(NOW));

        verify(iam).findVerifiedUserEmail(10L);
        verify(iam).findVerifiedUserEmail(20L);
        verify(iam).findVerifiedUserEmail(30L);
        verify(iam).findVerifiedUserEmail(31L);
        verify(sender, times(4)).send(any(EmailSender.Email.class));
    }

    @Test
    void openedPlatformCaseNotifiesRequesterAndPlatformAdministrators() {
        when(iam.findVerifiedAdministratorEmails()).thenReturn(List.of(
            new VerifiedUserEmailView(90L, "admin90@example.com"),
            new VerifiedUserEmailView(91L, "admin91@example.com")));

        handler.handle(SupportNotificationEvent.opened(new SupportCaseOpenedEvent(
            CASE_ID, 10L, SupportCaseType.PLATFORM_SUPPORT, null, NOW)).toIntegrationEvent(NOW));

        verify(iam).findVerifiedUserEmail(10L);
        verify(iam).findVerifiedAdministratorEmails();
        verify(sender, times(3)).send(any(EmailSender.Email.class));
        verifyNoInteractions(eventManagement);
    }

    @Test
    void agentReplyNotifiesRequester() {
        handler.handle(SupportNotificationEvent.messageAdded(new SupportMessageAddedEvent(
            CASE_ID, 20L, MessageAuthorType.SUPPORT_AGENT, SupportCaseType.ATTENDEE_SUPPORT,
            EVENT_ID, 10L, 20L, NOW)).toIntegrationEvent(NOW));

        verify(iam).findVerifiedUserEmail(10L);
        verify(sender).send(any(EmailSender.Email.class));
    }

    @Test
    void requesterReplyNotifiesAssignedAgent() {
        handler.handle(SupportNotificationEvent.messageAdded(new SupportMessageAddedEvent(
            CASE_ID, 10L, MessageAuthorType.REQUESTER, SupportCaseType.ATTENDEE_SUPPORT,
            EVENT_ID, 10L, 30L, NOW)).toIntegrationEvent(NOW));

        verify(iam).findVerifiedUserEmail(30L);
        verify(sender).send(any(EmailSender.Email.class));
    }

    @Test
    void requesterReplyToUnassignedAttendeeCaseNotifiesEventStaff() {
        handler.handle(SupportNotificationEvent.messageAdded(new SupportMessageAddedEvent(
            CASE_ID, 10L, MessageAuthorType.REQUESTER, SupportCaseType.ATTENDEE_SUPPORT,
            EVENT_ID, 10L, null, NOW)).toIntegrationEvent(NOW));

        verify(iam).findVerifiedUserEmail(20L);
        verify(iam).findVerifiedUserEmail(30L);
        verify(iam).findVerifiedUserEmail(31L);
        verify(sender, times(3)).send(any(EmailSender.Email.class));
    }

    @Test
    void requesterReplyToUnassignedPlatformCaseNotifiesAdministrators() {
        when(iam.findVerifiedAdministratorEmails()).thenReturn(List.of(
            new VerifiedUserEmailView(90L, "admin90@example.com")));

        handler.handle(SupportNotificationEvent.messageAdded(new SupportMessageAddedEvent(
            CASE_ID, 10L, MessageAuthorType.REQUESTER, SupportCaseType.PLATFORM_SUPPORT,
            null, 10L, null, NOW)).toIntegrationEvent(NOW));

        verify(iam).findVerifiedAdministratorEmails();
        verify(sender).send(any(EmailSender.Email.class));
        verifyNoInteractions(eventManagement);
    }

    @Test
    void missingEventRoutingEscalatesAttendeeCaseToPlatformAdministrators() {
        when(eventManagement.supportRouting(EVENT_ID)).thenReturn(Optional.empty());
        when(iam.findVerifiedAdministratorEmails()).thenReturn(List.of(
            new VerifiedUserEmailView(90L, "admin90@example.com")));

        handler.handle(SupportNotificationEvent.opened(new SupportCaseOpenedEvent(
            CASE_ID, 10L, SupportCaseType.ATTENDEE_SUPPORT, EVENT_ID, NOW)).toIntegrationEvent(NOW));

        verify(iam).findVerifiedUserEmail(10L);
        verify(iam).findVerifiedAdministratorEmails();
        verify(sender, times(2)).send(any(EmailSender.Email.class));
    }

    @Test
    void assignmentNotifiesRequesterAndAssignedSupportAgent() {
        handler.handle(SupportNotificationEvent.assigned(new SupportCaseAssignedEvent(
            CASE_ID, 30L, 20L, SupportCaseType.ATTENDEE_SUPPORT, EVENT_ID, 10L, NOW)).toIntegrationEvent(NOW));

        verify(iam).findVerifiedUserEmail(10L);
        verify(iam).findVerifiedUserEmail(30L);
        verify(sender, times(2)).send(any(EmailSender.Email.class));
    }

    @Test
    void emailFailurePropagatesSoOutboxCanRetry() {
        when(iam.findVerifiedUserEmail(10L)).thenThrow(new IllegalStateException("IAM unavailable"));
        assertThrows(IllegalStateException.class, () -> handler.handle(
            SupportNotificationEvent.opened(new SupportCaseOpenedEvent(
                CASE_ID, 10L, SupportCaseType.PLATFORM_SUPPORT, null, NOW)).toIntegrationEvent(NOW)));
        verify(sender, never()).send(any());
    }
}
