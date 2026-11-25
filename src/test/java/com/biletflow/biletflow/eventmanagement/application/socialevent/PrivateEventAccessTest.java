package com.biletflow.biletflow.eventmanagement.application.socialevent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository.EventAuditType;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.command.DefineRefundPolicyCommand;
import com.biletflow.biletflow.eventmanagement.application.socialevent.command.RemoveRefundPolicyCommand;
import com.biletflow.biletflow.eventmanagement.application.socialevent.command.RotatePrivateAccessCommand;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.eventmanagement.ohs.DefaultEventManagementOhs;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrivateEventAccessTest {

    private static final long ORGANIZER = 10L;
    private static final long ATTENDEE = 20L;
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final EventDateRange DATES = new EventDateRange(NOW.plusSeconds(7 * 86_400), NOW.plusSeconds(7 * 86_400 + 7_200));

    private final SocialEventRepository repository = mock(SocialEventRepository.class);
    private final EventAuditService audit = mock(EventAuditService.class);
    private Long user = ORGANIZER;
    private final CurrentActor actor = () -> Optional.ofNullable(user);
    private final EventQueryService queries = new EventQueryService(repository, actor);
    private final EventService service = new EventService(
        repository,
        mock(VenueLayoutRepository.class),
        audit,
        actor,
        Clock.fixed(NOW, ZoneOffset.UTC),
        mock(TransactionLocks.class),
        mock(EventBus.class)
    );
    private final DefaultEventManagementOhs ohs = new DefaultEventManagementOhs(repository, mock(VenueLayoutRepository.class), audit);
    private SocialEvent event;

    @BeforeEach
    void publishedPrivateEvent() {
        event = SocialEvent.createDraft(
            ORGANIZER,
            "Private party",
            "Invite only",
            "Music",
            "",
            EventVisibility.PRIVATE,
            DATES,
            new RegistrationWindow.AlwaysOpen()
        );
        event.attachVenue(Venue.createGeneralAdmission("Hall", "Astana", 50));
        event.publish();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private String code() {
        return event.getPrivateAccessCode().orElseThrow().value();
    }

    @Test
    void privateEventPageNeedsTheAccessCode() {
        assertThrows(SocialEventNotFoundException.class, () -> queries.getPublic(event.getId()));
        assertThrows(SocialEventNotFoundException.class, () -> queries.getPublic(event.getId(), "wrong-code-wrong-code"));
        assertEquals("Private party", queries.getPublic(event.getId(), code()).title());
    }

    @Test
    void organizerSeesTheInviteCodeInTheManagementView() {
        assertEquals(code(), queries.getForManagement(event.getId()).privateAccessCode());
    }

    @Test
    void ohsGrantsAccessWithTheCodeOrToTheOrganizer() {
        var id = event.getId().value();
        assertTrue(ohs.grantsAttendeeAccess(id, ATTENDEE, code()));
        assertTrue(ohs.grantsAttendeeAccess(id, ORGANIZER, null));
        assertFalse(ohs.grantsAttendeeAccess(id, ATTENDEE, null));
        assertFalse(ohs.grantsAttendeeAccess(id, null, "wrong-code-wrong-code"));
        assertFalse(ohs.salesScope(id, NOW).publiclyReadable());
    }

    @Test
    void rotationRevokesTheOldLinkAndIsAudited() {
        String old = code();

        var view = service.handle(new RotatePrivateAccessCommand(event.getId()));

        assertNotEquals(old, view.accessCode());
        assertThrows(SocialEventNotFoundException.class, () -> queries.getPublic(event.getId(), old));
        assertEquals(event.getId().value(), queries.getPublic(event.getId(), view.accessCode()).id());
        verify(audit).record(eq(event.getId().value()), eq(ORGANIZER), eq(EventAuditType.PRIVATE_ACCESS_ROTATED), anyString());
    }

    @Test
    void onlyTheOrganizerCanRotateTheCode() {
        user = ATTENDEE;
        assertThrows(AuthorizationException.class, () -> service.handle(new RotatePrivateAccessCommand(event.getId())));
    }

    @Test
    void refundPolicyIsShownToAttendeesAndAudited() {
        service.handle(new DefineRefundPolicyCommand(event.getId(), true, 48, "Email us before the cutoff"));

        var policy = queries.getPublic(event.getId(), code()).refundPolicy();
        assertTrue(policy.refundsAllowed());
        assertEquals(48, policy.cutoffHoursBeforeStart());
        assertEquals(DATES.startAt().minusSeconds(48 * 3_600), policy.refundableUntil());
        assertEquals("Email us before the cutoff", policy.terms());
        assertEquals(policy, queries.getForManagement(event.getId()).refundPolicy());
        verify(audit).record(
            eq(event.getId().value()),
            eq(ORGANIZER),
            eq(EventAuditType.REFUND_POLICY_UPDATED),
            eq("Refund policy set: Refunds until 48 hours before the event")
        );
    }

    @Test
    void removingThePolicyHidesItAndIsAuditedOnce() {
        service.handle(new DefineRefundPolicyCommand(event.getId(), false, 0, null));
        service.handle(new RemoveRefundPolicyCommand(event.getId()));
        service.handle(new RemoveRefundPolicyCommand(event.getId()));

        assertNull(queries.getPublic(event.getId(), code()).refundPolicy());
        verify(audit, times(1)).record(any(), any(), eq(EventAuditType.REFUND_POLICY_REMOVED), anyString());
    }

    @Test
    void onlyTheOrganizerCanSetThePolicy() {
        user = ATTENDEE;
        assertThrows(AuthorizationException.class, () -> service.handle(new DefineRefundPolicyCommand(event.getId(), true, 0, null)));
    }

    @Test
    void invalidPolicyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.handle(new DefineRefundPolicyCommand(event.getId(), false, 24, null)));
    }
}
