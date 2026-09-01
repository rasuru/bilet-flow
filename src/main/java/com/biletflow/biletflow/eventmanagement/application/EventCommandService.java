package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.iam.security.SecurityUtils;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class EventCommandService {

    private final SocialEventRepository eventRepository;
    private final EventAuditService auditService;

    public EventCommandService(SocialEventRepository eventRepository, EventAuditService auditService) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "SocialEventRepository cannot be null");
        this.auditService = Objects.requireNonNull(auditService, "EventAuditService cannot be null");
    }

    @Transactional
    public SocialEventId handle(CreateDraftEventCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent draftEvent = SocialEvent.createDraft(
            currentUserId,
            command.title(),
            command.description(),
            command.category(),
            command.imageUrl(),
            command.visibility(),
            command.dateRange(),
            command.registrationWindow(),
            currentUserId
        );

        SocialEvent savedEvent = eventRepository.save(draftEvent);

        auditService.record(savedEvent.getId().value(), currentUserId, EventAuditRepository.EventAuditType.CREATED, "Event draft created");

        return savedEvent.getId();
    }

    @Transactional
    public void handle(UpdateEventDetailsCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.updateDetails(
            command.title(),
            command.description(),
            command.category(),
            command.imageUrl(),
            command.visibility(),
            command.dateRange(),
            command.registrationWindow(),
            currentUserId
        );

        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.UPDATED, "Event details updated");
    }

    @Transactional
    public void handle(PublishSocialEventCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.publish(currentUserId);

        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.PUBLISHED, "Event published");
    }

    @Transactional
    public void handle(UnpublishEventCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.unpublish(currentUserId);

        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.UNPUBLISHED, "Event unpublished");
    }

    @Transactional
    public void handle(CancelSocialEventCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.cancel(command.cancellationTime(), currentUserId);

        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.CANCELLED, "Event cancelled");
    }

    @Transactional
    public SocialEventId handle(DuplicateEventCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent sourceEvent = eventRepository
            .findById(command.sourceEventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.sourceEventId().value().toString()));

        assertCanModifyEvent(sourceEvent);

        SocialEvent duplicatedEvent = sourceEvent.duplicate(
            command.executionTime(),
            command.newDateRange(),
            command.newRegistrationWindow(),
            currentUserId
        );

        SocialEvent savedEvent = eventRepository.save(duplicatedEvent);

        auditService.record(savedEvent.getId().value(), currentUserId, EventAuditRepository.EventAuditType.CREATED, "Event duplicated");

        return savedEvent.getId();
    }

    @Transactional
    public void handle(AttachVenueCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.attachVenue(command.venue(), currentUserId);

        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.VENUE_ATTACHED,
            "Venue attached to event"
        );
    }

    @Transactional
    public void handle(AssignStaffCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.assignStaff(command.staffUserId(), command.role(), currentUserId);

        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.STAFF_ASSIGNED,
            "Staff member assigned"
        );
    }

    @Transactional
    public void handle(RemoveStaffCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.removeStaff(command.staffUserId(), currentUserId);

        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.STAFF_REMOVED,
            "Staff member removed"
        );
    }

    @Transactional
    public void handle(CreateTicketTypeCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.addTicketType(
            command.name(),
            command.description(),
            command.pricing(),
            command.quantity(),
            command.salesWindow(),
            command.maxPerOrder(),
            currentUserId
        );

        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.TICKET_TYPE_ADDED,
            "Ticket type added"
        );
    }

    @Transactional
    public void handle(UpdateTicketTypeCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        TicketType ticketType = event
            .getTicketTypes()
            .stream()
            .filter(tt -> tt.getId().equals(command.ticketTypeId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("TicketType not found: " + command.ticketTypeId().value()));

        ticketType.updateDetails(command.name(), command.description(), command.salesWindow());
        ticketType.updateQuantity(command.quantity());

        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.UPDATED, "Ticket type updated");
    }

    @Transactional
    public void handle(HideTicketTypeCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.hideTicketType(command.ticketTypeId(), currentUserId);

        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.TICKET_TYPE_HIDDEN,
            "Ticket type hidden"
        );
    }

    @Transactional
    public void handle(RevealTicketTypeCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = eventRepository
            .findById(command.eventId())
            .orElseThrow(() -> new SocialEventNotFoundException(command.eventId().value().toString()));

        assertCanModifyEvent(event);

        event.revealTicketType(command.ticketTypeId(), currentUserId);

        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.TICKET_TYPE_REVEALED,
            "Ticket type revealed"
        );
    }

    private Long getAuthenticatedUserIdOrThrow() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() ->
            new AccessDeniedException("User must be authenticated to perform this operation")
        );
    }

    private void assertCanModifyEvent(SocialEvent event) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        boolean isOrganizer = event.getOrganizerId().equals(currentUserId);

        if (!isOrganizer) {
            throw new AccessDeniedException("User is not authorized to modify event: " + event.getId().value());
        }
    }
}
