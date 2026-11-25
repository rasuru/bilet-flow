package com.biletflow.biletflow.eventmanagement.application.socialevent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.application.Transactions;
import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.AttendeeInvitationRepository.Invitation;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

class PrivateAttendeeAccessTest {

    private static final long ORGANIZER = 1L;
    private static final long ATTENDEE = 2L;
    private static final String EMAIL = "guest@example.com";

    private final SocialEventRepository events = mock(SocialEventRepository.class);
    private final AttendeeInvitationRepository invitations = mock(AttendeeInvitationRepository.class);
    private final CurrentActor actor = mock(CurrentActor.class);
    private final IamOhs users = mock(IamOhs.class);
    private final EmailSender mail = mock(EmailSender.class);
    private final PrivateAttendeeAccess access = new PrivateAttendeeAccess(
        events,
        invitations,
        actor,
        users,
        mail,
        mock(EventAuditService.class),
        new Transactions() {
            @Override
            public <T> T required(Supplier<T> work) {
                return work.get();
            }
        },
        mock(TransactionLocks.class),
        URI.create("http://localhost:5173")
    );

    @Test
    void invitationGrantRequiresTheLinkAndTheMatchingAccount() {
        var event = draft(EventVisibility.PRIVATE);
        event.publish();
        UUID eventId = event.getId().value();
        var invitation = new Invitation(UUID.randomUUID(), eventId, EMAIL, UUID.randomUUID(), null);
        when(invitations.findByToken(eventId, invitation.token())).thenReturn(Optional.of(invitation));
        when(actor.requireUserId()).thenReturn(ATTENDEE);
        when(users.findVerifiedUserEmail(ATTENDEE)).thenReturn(Optional.of(new VerifiedUserEmailView(ATTENDEE, "other@example.com")));

        assertFalse(access.canReadEvent(event, null));
        assertFalse(access.canReadEvent(event, ATTENDEE));
        assertTrue(access.canReadEvent(event, ORGANIZER));
        assertThrows(EntityNotFoundException.class, () -> access.accept(eventId, UUID.randomUUID()));
        assertThrows(AuthorizationException.class, () -> access.accept(eventId, invitation.token()));
        verify(invitations, never()).accept(any(), anyLong());

        when(users.findVerifiedUserEmail(ATTENDEE)).thenReturn(
            Optional.of(new VerifiedUserEmailView(ATTENDEE, EMAIL.toUpperCase(java.util.Locale.ROOT)))
        );
        access.accept(eventId, invitation.token());
        verify(invitations).accept(invitation.id(), ATTENDEE);
        when(invitations.hasAccepted(eventId, ATTENDEE)).thenReturn(true);
        assertTrue(access.canReadEvent(event, ATTENDEE));

        event.unpublish();
        assertFalse(access.canReadEvent(event, ATTENDEE));
        event.publish();
        when(invitations.hasAccepted(eventId, ATTENDEE)).thenReturn(false);
        assertFalse(access.canReadEvent(event, ATTENDEE));
    }

    @Test
    void invitationIsSavedBeforeSendingAndTheSameLinkCanBeResent() {
        var event = draft(EventVisibility.PRIVATE);
        UUID eventId = event.getId().value();
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(actor.requireUserId()).thenReturn(ORGANIZER);
        doThrow(new MailSendException("SMTP unavailable")).doNothing().when(mail).send(any());

        assertThrows(PrivateAttendeeAccess.InvitationDeliveryException.class, () -> access.invite(eventId, " Guest@Example.com "));
        var insertion = ArgumentCaptor.forClass(Invitation.class);
        verify(invitations).insert(insertion.capture());
        var persisted = insertion.getValue();
        assertEquals(EMAIL, persisted.email());

        when(invitations.findById(eventId, persisted.id())).thenReturn(Optional.of(persisted));
        access.resend(eventId, persisted.id());
        var messages = ArgumentCaptor.forClass(EmailSender.Email.class);
        verify(mail, times(2)).send(messages.capture());
        assertEquals(messages.getAllValues().getFirst().text(), messages.getValue().text());
        assertTrue(
            messages
                .getValue()
                .text()
                .contains("/events/" + eventId + "/invitation#token=" + persisted.token())
        );

        when(actor.requireUserId()).thenReturn(ATTENDEE);
        assertThrows(AuthorizationException.class, () -> access.revoke(eventId, persisted.id()));
        verify(invitations, never()).delete(any(), any());
    }

    @Test
    void publishedUnlistedEventIsReadableWithoutAnInvitation() {
        var event = draft(EventVisibility.UNLISTED);
        assertFalse(access.canReadEvent(event, null));
        event.publish();
        assertTrue(access.canReadEvent(event, null));
        verifyNoInteractions(invitations);
    }

    private static SocialEvent draft(EventVisibility visibility) {
        var event = SocialEvent.createDraft(
            ORGANIZER,
            "Astana chamber concert",
            "",
            "Music",
            "",
            visibility,
            new EventDateRange(Instant.parse("2026-12-04T13:00:00Z"), Instant.parse("2026-12-04T14:00:00Z")),
            new RegistrationWindow.AlwaysOpen()
        );
        event.attachVenue(Venue.createGeneralAdmission("Chamber hall", "Astana", 80));
        return event;
    }
}
