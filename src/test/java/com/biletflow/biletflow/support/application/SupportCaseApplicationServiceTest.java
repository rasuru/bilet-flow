package com.biletflow.biletflow.support.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.support.application.command.*;
import com.biletflow.biletflow.support.application.event.*;
import com.biletflow.biletflow.support.domain.supportcase.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SupportCaseApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private SupportCaseRepository repository;
    private CurrentActor actor;
    private ApplicationEventPublisher events;
    private SupportCaseApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(SupportCaseRepository.class);
        actor = mock(CurrentActor.class);
        events = mock(ApplicationEventPublisher.class);
        service = new SupportCaseApplicationService(repository, actor, events,
            Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(SupportCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SupportCase caseFor(Long requester) {
        return SupportCase.open(SupportCaseType.ATTENDEE_SUPPORT, requester,
            "Ticket issue", IssueCategory.TICKET_DELIVERY, "I need help",
            null, null, null, NOW);
    }

    @Test
    void openCreatesCaseForCurrentActorAndPublishesEvent() {
        when(actor.requireUserId()).thenReturn(101L);
        var result = service.open(new OpenSupportCaseCommand(
            SupportCaseType.ATTENDEE_SUPPORT, "Ticket issue", IssueCategory.TICKET_DELIVERY,
            "I need help", null, null, null));

        assertEquals(101L, result.requesterUserId());
        assertEquals(CaseStatus.OPEN, result.status());
        assertEquals(NOW, result.createdAt());
        verify(repository).save(any(SupportCase.class));
        verify(events).publishEvent(any(SupportCaseOpenedEvent.class));
    }

    @Test
    void requesterCanReadOwnCase() {
        when(actor.requireUserId()).thenReturn(101L);
        UUID id = UUID.randomUUID();
        SupportCase supportCase = caseFor(101L);
        when(repository.findById(new SupportCaseId(id))).thenReturn(Optional.of(
            SupportCase.rehydrate(new SupportCaseId(id), supportCase.getType(), 101L,
                supportCase.getSubject(), supportCase.getCategory(), null, null, null,
                CaseStatus.OPEN, null, null, NOW, NOW, null, supportCase.getMessages())));

        assertEquals(id, service.get(id).id());
    }

    @Test
    void requesterCannotReadAnotherUsersCase() {
        when(actor.requireUserId()).thenReturn(101L);
        SupportCase supportCase = caseFor(202L);
        UUID id = supportCase.getId().value();
        when(repository.findById(new SupportCaseId(id))).thenReturn(Optional.of(supportCase));

        assertThrows(AuthorizationException.class, () -> service.get(id));
    }

    @Test
    void missingCaseThrowsNotFound() {
        when(actor.requireUserId()).thenReturn(101L);
        UUID id = UUID.randomUUID();
        when(repository.findById(new SupportCaseId(id))).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.get(id));
    }

    @Test
    void requesterListIsScopedToCurrentUser() {
        when(actor.requireUserId()).thenReturn(101L);
        when(actor.isAdministrator()).thenReturn(false);
        when(repository.findByRequesterUserId(101L)).thenReturn(List.of(caseFor(101L)));

        assertEquals(1, service.list().size());
        verify(repository).findByRequesterUserId(101L);
        verify(repository, never()).findAll();
    }

    @Test
    void administratorListReturnsAllCases() {
        when(actor.requireUserId()).thenReturn(999L);
        when(actor.isAdministrator()).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of(caseFor(101L), caseFor(202L)));

        assertEquals(2, service.list().size());
        verify(repository).findAll();
    }

    @Test
    void nonAdministratorCannotAssignCase() {
        when(actor.requireAdministrator()).thenThrow(new AuthorizationException("Administrator access is required"));

        assertThrows(AuthorizationException.class,
            () -> service.assign(new AssignSupportCaseCommand(UUID.randomUUID(), 303L)));
        verify(repository, never()).save(any());
    }

    @Test
    void administratorCanAssignCaseAndPublishesEvent() {
        when(actor.requireAdministrator()).thenReturn(999L);
        SupportCase supportCase = caseFor(101L);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        var result = service.assign(new AssignSupportCaseCommand(supportCase.getId().value(), 303L));

        assertEquals(303L, result.assignedSupportUserId());
        verify(repository).save(supportCase);
        verify(events).publishEvent(any(SupportCaseAssignedEvent.class));
    }

    @Test
    void requesterReplyPersistsMessageAndPublishesEvent() {
        when(actor.requireUserId()).thenReturn(101L);
        SupportCase supportCase = caseFor(101L);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        var result = service.reply(new ReplyToSupportCaseCommand(supportCase.getId().value(), "More details"));

        assertEquals(2, result.messages().size());
        verify(repository).save(supportCase);
        verify(events).publishEvent(any(SupportMessageAddedEvent.class));
    }

    @Test
    void administratorCanChangeStatusAndPublishesEvent() {
        when(actor.requireAdministrator()).thenReturn(999L);
        SupportCase supportCase = caseFor(101L);
        when(repository.findById(supportCase.getId())).thenReturn(Optional.of(supportCase));

        var result = service.changeStatus(
            new ChangeSupportCaseStatusCommand(supportCase.getId().value(), CaseStatus.IN_PROGRESS));

        assertEquals(CaseStatus.IN_PROGRESS, result.status());
        verify(events).publishEvent(any(SupportCaseStatusChangedEvent.class));
    }
}
