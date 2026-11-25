package com.biletflow.biletflow.eventmanagement.application.socialevent;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.application.Transactions;
import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository.EventAuditType;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.AttendeeInvitationRepository.Invitation;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateAttendeeAccess {

    private final SocialEventRepository events;
    private final AttendeeInvitationRepository invitations;
    private final CurrentActor actor;
    private final IamOhs users;
    private final EmailSender mail;
    private final EventAuditService audit;
    private final Transactions transactions;
    private final TransactionLocks locks;
    private final URI frontend;

    public PrivateAttendeeAccess(
        SocialEventRepository events,
        AttendeeInvitationRepository invitations,
        CurrentActor actor,
        IamOhs users,
        EmailSender mail,
        EventAuditService audit,
        Transactions transactions,
        TransactionLocks locks,
        @Value("${biletflow.frontend}") URI frontend
    ) {
        this.events = events;
        this.invitations = invitations;
        this.actor = actor;
        this.users = users;
        this.mail = mail;
        this.audit = audit;
        this.transactions = transactions;
        this.locks = locks;
        this.frontend = frontend;
    }

    public boolean canReadEvent(SocialEvent event, Long userId) {
        return (
            isAttendeeReadable(event) && (event.getVisibility() != EventVisibility.PRIVATE || (userId != null && canAccess(event, userId)))
        );
    }

    public boolean canAccess(SocialEvent event, long userId) {
        return event.canBeManagedBy(userId) || (isAttendeeReadable(event) && invitations.hasAccepted(event.getId().value(), userId));
    }

    @Transactional(readOnly = true)
    public List<InvitationView> list(UUID eventId) {
        requireManagedEvent(eventId, actor.requireUserId());
        return invitations
            .findByEvent(eventId)
            .stream()
            .map(invitation -> new InvitationView(invitation.id(), invitation.email(), invitation.acceptedBy() != null))
            .toList();
    }

    public void invite(UUID eventId, String address) {
        long organizer = actor.requireUserId();
        String email = normalize(address);
        Delivery delivery = transactions.required(() -> {
            locks.lock("event-lifecycle:" + eventId);
            SocialEvent event = requireManagedEvent(eventId, organizer);
            requireInvitable(event);
            Invitation invitation = invitations.findByEmail(eventId, email).orElseGet(() -> {
                var created = new Invitation(UUID.randomUUID(), eventId, email, UUID.randomUUID(), null);
                invitations.insert(created);
                audit.record(eventId, organizer, EventAuditType.UPDATED, "Attendee invitation added");
                return created;
            });
            return new Delivery(event.getTitle(), invitation);
        });
        sendInvitation(delivery);
    }

    public void resend(UUID eventId, UUID id) {
        long organizer = actor.requireUserId();
        Delivery delivery = transactions.required(() -> {
            locks.lock("event-lifecycle:" + eventId);
            SocialEvent event = requireManagedEvent(eventId, organizer);
            requireInvitable(event);
            var invitation = invitations.findById(eventId, id).orElseThrow(PrivateAttendeeAccess::missingInvitation);
            return new Delivery(event.getTitle(), invitation);
        });
        sendInvitation(delivery);
    }

    @Transactional
    public void revoke(UUID eventId, UUID id) {
        long organizer = actor.requireUserId();
        locks.lock("event-lifecycle:" + eventId);
        requireManagedEvent(eventId, organizer);
        if (invitations.delete(eventId, id)) {
            audit.record(eventId, organizer, EventAuditType.UPDATED, "Attendee invitation revoked");
        }
    }

    @Transactional
    public void accept(UUID eventId, UUID token) {
        long userId = actor.requireUserId();
        locks.lock("event-lifecycle:" + eventId);
        var invitation = invitations.findByToken(eventId, token).orElseThrow(PrivateAttendeeAccess::missingInvitation);
        if (Long.valueOf(userId).equals(invitation.acceptedBy())) return;
        if (invitation.acceptedBy() != null) throw missingInvitation();

        // Account profile email can change. Requiring the one-time link proves access to the invited mailbox.
        boolean matches = users
            .findVerifiedUserEmail(userId)
            .map(user -> normalize(user.email()).equals(invitation.email()))
            .orElse(false);
        if (!matches) throw new AuthorizationException("Sign in with the email address that received this invitation");
        invitations.accept(invitation.id(), userId);
    }

    private void sendInvitation(Delivery delivery) {
        Invitation invitation = delivery.invitation();
        if (invitation.acceptedBy() != null) throw new IllegalStateException("This invitation was already accepted");
        URI url = frontend.resolve("/events/" + invitation.eventId() + "/invitation#token=" + invitation.token());
        try {
            mail.send(
                new EmailSender.Email(
                    UUID.randomUUID(),
                    invitation.email(),
                    "BiletFlow invitation: " + delivery.eventTitle(),
                    """
                    You have been invited to %s.

                    Accept your invitation using an account with this email address:
                    %s

                    You can open the event once the organizer publishes it.
                    """.formatted(delivery.eventTitle(), url)
                )
            );
        } catch (RuntimeException failure) {
            throw new InvitationDeliveryException();
        }
    }

    private SocialEvent requireManagedEvent(UUID eventId, long userId) {
        SocialEvent event = events
            .findById(new SocialEventId(eventId))
            .orElseThrow(() -> new SocialEventNotFoundException(eventId.toString()));
        if (!event.canBeManagedBy(userId)) throw new AuthorizationException("Only the organizer can manage attendee invitations");
        return event;
    }

    private static boolean isAttendeeReadable(SocialEvent event) {
        return event.getStatus() == SocialEventStatus.PUBLISHED || event.getStatus() == SocialEventStatus.CANCELLED;
    }

    private static void requireInvitable(SocialEvent event) {
        if (
            event.getVisibility() != EventVisibility.PRIVATE ||
            event.getStatus() == SocialEventStatus.CANCELLED ||
            event.getStatus() == SocialEventStatus.SUSPENDED
        ) {
            throw new IllegalStateException("Only active private events can issue attendee invitations");
        }
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static EntityNotFoundException missingInvitation() {
        return new EntityNotFoundException("Invitation is unavailable");
    }

    public record InvitationView(UUID id, String email, boolean accepted) {}

    public static class InvitationDeliveryException extends RuntimeException {}

    private record Delivery(String eventTitle, Invitation invitation) {}
}
