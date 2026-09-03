package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.dto.*;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.iam.security.SecurityUtils;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class EventCommandService {

    private final SocialEventRepository eventRepository;
    private final VenueLayoutRepository venueLayoutRepository;
    private final EventAuditService auditService;
    private final Clock clock;

    public EventCommandService(
        SocialEventRepository eventRepository,
        VenueLayoutRepository venueLayoutRepository,
        EventAuditService auditService,
        Clock clock
    ) {
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.venueLayoutRepository = Objects.requireNonNull(venueLayoutRepository);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
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
            command.registrationWindow()
        );

        SocialEvent savedEvent = eventRepository.save(draftEvent);

        auditService.record(savedEvent.getId().value(), currentUserId, EventAuditRepository.EventAuditType.CREATED, "Event draft created");

        return savedEvent.getId();
    }

    @Transactional
    public void handle(UpdateEventDetailsCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        event.updateDetails(
            command.title(),
            command.description(),
            command.category(),
            command.imageUrl(),
            command.visibility(),
            command.dateRange(),
            command.registrationWindow()
        );

        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.UPDATED, "Event details updated");
    }

    @Transactional
    public void handle(PublishSocialEventCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        event.publish();
        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.PUBLISHED, "Event published");
    }

    @Transactional
    public void handle(UnpublishEventCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        event.unpublish();
        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.UNPUBLISHED, "Event unpublished");
    }

    @Transactional
    public void handle(CancelSocialEventCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        event.cancel(command.cancellationTime());
        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.CANCELLED, "Event cancelled");
    }

    @Transactional
    public SocialEventId handle(DuplicateEventCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent source = requireEvent(command.sourceEventId());
        assertCanModifyEvent(source, currentUserId);

        SocialEvent duplicated = source.duplicate(command.newDateRange(), command.newRegistrationWindow());

        SocialEvent saved = eventRepository.save(duplicated);

        auditService.record(saved.getId().value(), currentUserId, EventAuditRepository.EventAuditType.CREATED, "Event duplicated");

        return saved.getId();
    }

    @Transactional
    public void handle(AttachVenueCommand command) {
        Long currentUserId = getAuthenticatedUserIdOrThrow();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        validateVenueConfiguration(command.venue());

        event.attachVenue(command.venue());
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

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        event.assignStaff(command.staffUserId(), command.role(), Instant.now(clock));

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

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        event.removeStaff(command.staffUserId());
        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.STAFF_REMOVED,
            "Staff member removed"
        );
    }

    private void validateVenueConfiguration(Venue venue) {
        if (!(venue.getSeatingConfig() instanceof Venue.AssignedSeating assigned)) {
            return;
        }

        VenueLayout layout = venueLayoutRepository
            .findById(assigned.layoutId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown VenueLayoutId: " + assigned.layoutId().value()));

        if (venue.getTotalCapacity() > layout.getSeats().size()) {
            throw new IllegalArgumentException("Venue capacity cannot exceed assigned-seating layout size");
        }
    }

    private SocialEvent requireEvent(SocialEventId eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new SocialEventNotFoundException(eventId.value().toString()));
    }

    private Long getAuthenticatedUserIdOrThrow() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() ->
            new AccessDeniedException("User must be authenticated to perform this operation")
        );
    }

    private void assertCanModifyEvent(SocialEvent event, Long currentUserId) {
        if (!event.getOrganizerId().equals(currentUserId)) {
            throw new AccessDeniedException("User is not authorized to modify event: " + event.getId().value());
        }
    }
}
