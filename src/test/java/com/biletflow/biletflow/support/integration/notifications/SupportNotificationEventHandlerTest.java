package com.biletflow.biletflow.support.integration.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.support.application.event.*;
import com.biletflow.biletflow.support.domain.supportcase.*;
import java.time.Instant;
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
    void caseOpenedNotifiesRequester() {
        handler.onOpened(new SupportCaseOpenedEvent(UUID.randomUUID(), 10L, now));

        verify(iam).findVerifiedUserEmail(10L);
        verify(sender).send(any(EmailSender.Email.class));
    }

    @Test
    void agentReplyNotifiesRequester() {
        SupportCase supportCase = SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, 10L,
            "Issue", IssueCategory.OTHER, "Initial", null, null, null, now);
        supportCase.assignTo(20L, now.plusSeconds(1));
        when(cases.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        handler.onMessage(new SupportMessageAddedEvent(
            supportCase.getId().value(), 20L, MessageAuthorType.SUPPORT_AGENT, now.plusSeconds(2)));

        verify(iam).findVerifiedUserEmail(10L);
        verify(sender).send(any(EmailSender.Email.class));
    }

    @Test
    void requesterReplyNotifiesAssignedAgent() {
        SupportCase supportCase = SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, 10L,
            "Issue", IssueCategory.OTHER, "Initial", null, null, null, now);
        supportCase.assignTo(20L, now.plusSeconds(1));
        when(cases.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        handler.onMessage(new SupportMessageAddedEvent(
            supportCase.getId().value(), 10L, MessageAuthorType.REQUESTER, now.plusSeconds(2)));

        verify(iam).findVerifiedUserEmail(20L);
        verify(sender).send(any(EmailSender.Email.class));
    }

    @Test
    void missingAssignedAgentDoesNotSendRequesterReplyNotification() {
        SupportCase supportCase = SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, 10L,
            "Issue", IssueCategory.OTHER, "Initial", null, null, null, now);
        when(cases.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        handler.onMessage(new SupportMessageAddedEvent(
            supportCase.getId().value(), 10L, MessageAuthorType.REQUESTER, now.plusSeconds(1)));

        verifyNoInteractions(iam, sender);
    }

    @Test
    void emailFailureIsContained() {
        when(iam.findVerifiedUserEmail(10L)).thenThrow(new IllegalStateException("IAM unavailable"));

        handler.onOpened(new SupportCaseOpenedEvent(UUID.randomUUID(), 10L, now));

        verify(sender, never()).send(any());
    }
}
