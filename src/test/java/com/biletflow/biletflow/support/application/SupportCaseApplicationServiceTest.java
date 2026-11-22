package com.biletflow.biletflow.support.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.ordercheckout.ohs.OrderCheckoutOhs;
import com.biletflow.biletflow.ordercheckout.ohs.dto.SupportOrderReference;
import com.biletflow.biletflow.support.application.command.*;
import com.biletflow.biletflow.support.application.event.*;
import com.biletflow.biletflow.support.domain.supportcase.*;
import com.biletflow.biletflow.ticketinventory.ohs.TicketInventoryOhs;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketSupportReferenceView;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.biletflow.biletflow.support.application.view.SupportCaseView;

class SupportCaseApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("d7a0fd87-6ee4-4d95-ae58-fcf3f153b69e");
    private static final UUID ORDER_ID = UUID.fromString("3b8d45ce-1e38-4d6c-9e4e-41e85e034e38");
    private static final UUID TICKET_ID = UUID.fromString("a27c06ab-ea36-4760-b8e8-6e85d060b8d2");
    private static final Long REQUESTER = 101L;
    private static final Long ORGANIZER = 202L;
    private static final Long EVENT_STAFF = 303L;
    private static final Long ADMIN = 999L;

    private SupportCaseRepository repository;
    private CurrentActor actor;
    private ApplicationEventPublisher events;
    private EventManagementOhs eventManagement;
    private OrderCheckoutOhs orderCheckout;
    private TicketInventoryOhs ticketInventory;
    private IamOhs iam;
    private SupportCaseApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(SupportCaseRepository.class);
        actor = mock(CurrentActor.class);
        events = mock(ApplicationEventPublisher.class);
        eventManagement = mock(EventManagementOhs.class);
        orderCheckout = mock(OrderCheckoutOhs.class);
        ticketInventory = mock(TicketInventoryOhs.class);
        iam = mock(IamOhs.class);
        service = new SupportCaseApplicationService(repository, actor, events,
            Clock.fixed(NOW, ZoneOffset.UTC), eventManagement, orderCheckout, ticketInventory, iam);
        when(repository.save(any(SupportCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventManagement.supportRouting(EVENT_ID)).thenReturn(Optional.of(
            new EventManagementOhs.SupportRouting(EVENT_ID, ORGANIZER, List.of(EVENT_STAFF))));
        when(eventManagement.findSupportManagedEventIds(anyLong())).thenReturn(List.of());
        when(eventManagement.canHandleAttendeeSupport(any(), anyLong())).thenReturn(false);
        when(actor.isAdministrator()).thenReturn(false);
    }

    private SupportCase attendeeCase(Long requester) {
        return SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, requester,
            "Ticket issue", IssueCategory.TICKET_DELIVERY, "I need help",
            EVENT_ID, null, null, NOW);
    }

    private SupportCase platformCase(Long requester) {
        return SupportCase.open(SupportCaseType.PLATFORM_SUPPORT, requester,
            "Organizer account issue", IssueCategory.ACCOUNT, "I need help",
            null, null, null, NOW);
    }

    @Test
    void openCreatesCaseForCurrentActorAndPublishesEvent() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        var result = service.open(new OpenSupportCaseCommand(
            SupportCaseType.ATTENDEE_SUPPORT, "Ticket issue", IssueCategory.TICKET_DELIVERY,
            "I need help", EVENT_ID, null, null));

        assertEquals(REQUESTER, result.requesterUserId());
        assertEquals(EVENT_ID, result.eventId());
        assertEquals(CaseStatus.OPEN, result.status());
        assertEquals(NOW, result.createdAt());
        verify(repository).save(any(SupportCase.class));
        verify(events).publishEvent(any(SupportCaseOpenedEvent.class));
    }

    @Test
    void attendeeCaseRequiresEventOrValidatedOrderOrTicketContext() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        assertThrows(IllegalArgumentException.class, () -> service.open(new OpenSupportCaseCommand(
            SupportCaseType.ATTENDEE_SUPPORT, "Subject", IssueCategory.OTHER, "Message", null, null, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void orderReferenceIsValidatedAndEventIdIsDerivedFromOrder() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        when(orderCheckout.findSupportReference(ORDER_ID)).thenReturn(Optional.of(
            new SupportOrderReference(ORDER_ID, REQUESTER, EVENT_ID)));

        var result = service.open(new OpenSupportCaseCommand(
            SupportCaseType.ATTENDEE_SUPPORT, "Subject", IssueCategory.OTHER, "Message", null, ORDER_ID, null));

        assertEquals(EVENT_ID, result.eventId());
        assertEquals(ORDER_ID, result.orderId());
        verify(eventManagement).supportRouting(EVENT_ID);
    }

    @Test
    void orderOwnedByAnotherUserIsRejected() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        when(orderCheckout.findSupportReference(ORDER_ID)).thenReturn(Optional.of(
            new SupportOrderReference(ORDER_ID, 202L, EVENT_ID)));

        assertThrows(AuthorizationException.class, () -> service.open(new OpenSupportCaseCommand(
            SupportCaseType.ATTENDEE_SUPPORT, "Subject", IssueCategory.OTHER, "Message", null, ORDER_ID, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void ticketOwnedByAnotherUserIsRejected() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        when(ticketInventory.findSupportReference(TICKET_ID)).thenReturn(Optional.of(
            new TicketSupportReferenceView(TICKET_ID, ORDER_ID, EVENT_ID, 404L)));

        assertThrows(AuthorizationException.class, () -> service.open(new OpenSupportCaseCommand(
            SupportCaseType.ATTENDEE_SUPPORT, "Subject", IssueCategory.OTHER, "Message", null, null, TICKET_ID)));
        verify(repository, never()).save(any());
    }

    @Test
    void unclaimedTicketCanBeReferencedByItsOrderOwner() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        when(ticketInventory.findSupportReference(TICKET_ID)).thenReturn(Optional.of(
            new TicketSupportReferenceView(TICKET_ID, ORDER_ID, EVENT_ID, null)));
        when(orderCheckout.findSupportReference(ORDER_ID)).thenReturn(Optional.of(
            new SupportOrderReference(ORDER_ID, REQUESTER, EVENT_ID)));

        var result = service.open(new OpenSupportCaseCommand(
            SupportCaseType.ATTENDEE_SUPPORT, "Subject", IssueCategory.OTHER, "Message", null, null, TICKET_ID));

        assertEquals(EVENT_ID, result.eventId());
        assertEquals(ORDER_ID, result.orderId());
        assertEquals(TICKET_ID, result.ticketId());
    }

    @Test
    void requesterCanReadOwnCase() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        SupportCase supportCase = attendeeCase(REQUESTER);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        assertEquals(supportCase.getId().value(), service.get(supportCase.getId().value()).id());
    }

    @Test
    void strangerCannotReadAnotherUsersCase() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        SupportCase supportCase = attendeeCase(202L);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        assertThrows(AuthorizationException.class, () -> service.get(supportCase.getId().value()));
    }

    @Test
    void strangerReplyIsForbidden() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        SupportCase supportCase = attendeeCase(202L);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        assertThrows(AuthorizationException.class, () -> service.reply(
            new ReplyToSupportCaseCommand(supportCase.getId().value(), "I should not see this")));
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void organizerCanReadReplyAssignAndChangeStatusForTheirEventCase() {
        when(actor.requireUserId()).thenReturn(ORGANIZER);
        when(eventManagement.canHandleAttendeeSupport(EVENT_ID, ORGANIZER)).thenReturn(true);
        when(eventManagement.canHandleAttendeeSupport(EVENT_ID, EVENT_STAFF)).thenReturn(true);
        SupportCase supportCase = attendeeCase(REQUESTER);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        assertDoesNotThrow(() -> service.get(supportCase.getId().value()));
        assertEquals(MessageAuthorType.SUPPORT_AGENT, service.reply(
            new ReplyToSupportCaseCommand(supportCase.getId().value(), "We are investigating")).messages().getLast().authorType());
        assertEquals(EVENT_STAFF, service.assign(
            new AssignSupportCaseCommand(supportCase.getId().value(), EVENT_STAFF)).assignedSupportUserId());
        assertEquals(CaseStatus.IN_PROGRESS, service.changeStatus(
            new ChangeSupportCaseStatusCommand(supportCase.getId().value(), CaseStatus.IN_PROGRESS)).status());
    }

    @Test
    void assignedEventAdminCanReadReplyAssignAndChangeStatusForTheirEventCase() {
        when(actor.requireUserId()).thenReturn(EVENT_STAFF);
        when(eventManagement.canHandleAttendeeSupport(EVENT_ID, EVENT_STAFF)).thenReturn(true);
        when(eventManagement.canHandleAttendeeSupport(EVENT_ID, ORGANIZER)).thenReturn(true);
        when(eventManagement.findSupportManagedEventIds(EVENT_STAFF)).thenReturn(List.of(EVENT_ID));
        when(repository.findByRequesterUserId(EVENT_STAFF)).thenReturn(List.of());

        SupportCase supportCase = attendeeCase(REQUESTER);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));
        when(repository.findByEventIdIn(List.of(EVENT_ID))).thenReturn(List.of(supportCase));

        assertDoesNotThrow(() -> service.get(supportCase.getId().value()));
        assertEquals(MessageAuthorType.SUPPORT_AGENT, service.reply(
            new ReplyToSupportCaseCommand(supportCase.getId().value(), "I will investigate")).messages().getLast().authorType());
        assertEquals(ORGANIZER, service.assign(
            new AssignSupportCaseCommand(supportCase.getId().value(), ORGANIZER)).assignedSupportUserId());
        assertEquals(CaseStatus.IN_PROGRESS, service.changeStatus(
            new ChangeSupportCaseStatusCommand(supportCase.getId().value(), CaseStatus.IN_PROGRESS)).status());
        assertTrue(service.list().stream().anyMatch(view -> view.id().equals(supportCase.getId().value())));
    }

    @Test
    void suppliedEventMustMatchValidatedTicketContext() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        UUID anotherEventId = UUID.randomUUID();
        when(ticketInventory.findSupportReference(TICKET_ID)).thenReturn(Optional.of(
            new TicketSupportReferenceView(TICKET_ID, ORDER_ID, EVENT_ID, REQUESTER)));

        assertThrows(AuthorizationException.class, () -> service.open(new OpenSupportCaseCommand(
            SupportCaseType.ATTENDEE_SUPPORT, "Subject", IssueCategory.OTHER, "Message",
            anotherEventId, null, TICKET_ID)));
        verify(repository, never()).save(any());
    }

    @Test
    void platformCaseRejectsAttendeeContextReferences() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        assertThrows(IllegalArgumentException.class, () -> service.open(new OpenSupportCaseCommand(
            SupportCaseType.PLATFORM_SUPPORT, "Subject", IssueCategory.OTHER, "Message",
            EVENT_ID, null, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void organizerCannotAssignCaseToUnauthorizedUser() {
        when(actor.requireUserId()).thenReturn(ORGANIZER);
        when(eventManagement.canHandleAttendeeSupport(EVENT_ID, ORGANIZER)).thenReturn(true);
        when(eventManagement.canHandleAttendeeSupport(EVENT_ID, 404L)).thenReturn(false);
        SupportCase supportCase = attendeeCase(REQUESTER);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        assertThrows(AuthorizationException.class, () -> service.assign(
            new AssignSupportCaseCommand(supportCase.getId().value(), 404L)));
    }

    @Test
    void organizerListIncludesCasesForManagedEventsButNotPlatformCases() {
        when(actor.requireUserId()).thenReturn(ORGANIZER);
        when(eventManagement.findSupportManagedEventIds(ORGANIZER)).thenReturn(List.of(EVENT_ID));
        SupportCase own = attendeeCase(ORGANIZER);
        SupportCase eventCase = attendeeCase(REQUESTER);
        SupportCase platformCase = platformCase(505L);
        when(repository.findByRequesterUserId(ORGANIZER)).thenReturn(List.of(own));
        when(repository.findByEventIdIn(List.of(EVENT_ID))).thenReturn(List.of(eventCase, platformCase));

        List<SupportCaseView> result = service.list();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(view -> view.id().equals(eventCase.getId().value())));
        assertFalse(result.stream().anyMatch(view -> view.id().equals(platformCase.getId().value())));
    }

    @Test
    void administratorCanListAllCases() {
        when(actor.requireUserId()).thenReturn(ADMIN);
        when(actor.isAdministrator()).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of(attendeeCase(REQUESTER), platformCase(202L)));

        assertEquals(2, service.list().size());
        verify(repository).findAll();
    }

    @Test
    void administratorCanManagePlatformCase() {
        when(actor.requireUserId()).thenReturn(ADMIN);
        when(actor.isAdministrator()).thenReturn(true);
        SupportCase supportCase = platformCase(REQUESTER);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));
        when(iam.isPlatformAdministrator(ADMIN)).thenReturn(true);

        assertDoesNotThrow(() -> service.reply(new ReplyToSupportCaseCommand(supportCase.getId().value(), "Platform help")));
        assertEquals(CaseStatus.IN_PROGRESS, service.changeStatus(
            new ChangeSupportCaseStatusCommand(supportCase.getId().value(), CaseStatus.IN_PROGRESS)).status());
    }

    @Test
    void platformCaseCannotBeHandledByOrganizer() {
        when(actor.requireUserId()).thenReturn(ORGANIZER);
        SupportCase supportCase = platformCase(REQUESTER);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        assertThrows(AuthorizationException.class, () -> service.changeStatus(
            new ChangeSupportCaseStatusCommand(supportCase.getId().value(), CaseStatus.IN_PROGRESS)));
    }

    @Test
    void platformCaseAssignmentOnlyAllowsPlatformAdministrators() {
        when(actor.requireUserId()).thenReturn(ADMIN);
        when(actor.isAdministrator()).thenReturn(true);
        when(iam.isPlatformAdministrator(404L)).thenReturn(false);
        SupportCase supportCase = platformCase(REQUESTER);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        assertThrows(AuthorizationException.class, () -> service.assign(
            new AssignSupportCaseCommand(supportCase.getId().value(), 404L)));
    }

    @Test
    void missingCaseThrowsNotFound() {
        when(actor.requireUserId()).thenReturn(REQUESTER);
        UUID id = UUID.randomUUID();
        when(repository.findById(new SupportCaseId(id))).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.get(id));
    }
}
