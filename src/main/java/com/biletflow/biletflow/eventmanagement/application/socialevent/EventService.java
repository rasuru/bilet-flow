package com.biletflow.biletflow.eventmanagement.application.socialevent;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.command.*;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private final SocialEventRepository eventRepository;
    private final VenueLayoutRepository venueLayoutRepository;
    private final EventAuditService auditService;
    private final CurrentActor currentActor;
    private final Clock clock;

    public EventService(
        SocialEventRepository eventRepository,
        VenueLayoutRepository venueLayoutRepository,
        EventAuditService auditService,
        CurrentActor currentActor,
        Clock clock
    ) {
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.venueLayoutRepository = Objects.requireNonNull(venueLayoutRepository);
        this.auditService = Objects.requireNonNull(auditService);
        this.currentActor = Objects.requireNonNull(currentActor);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public SocialEventId handle(CreateDraftEventCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

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
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

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
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        event.publish();
        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.PUBLISHED, "Event published");
    }

    @Transactional
    public void handle(UnpublishEventCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        event.unpublish();
        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.UNPUBLISHED, "Event unpublished");
    }

    @Transactional
    public void handle(CancelSocialEventCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        event.cancel(command.cancellationTime());
        eventRepository.save(event);

        auditService.record(event.getId().value(), currentUserId, EventAuditRepository.EventAuditType.CANCELLED, "Event cancelled");
    }

    @Transactional
    public SocialEventId handle(DuplicateEventCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

        SocialEvent source = requireEvent(command.sourceEventId());
        assertCanModifyEvent(source, currentUserId);

        SocialEvent duplicated = source.duplicate(command.newDateRange(), command.newRegistrationWindow());

        SocialEvent saved = eventRepository.save(duplicated);

        auditService.record(saved.getId().value(), currentUserId, EventAuditRepository.EventAuditType.CREATED, "Event duplicated");

        return saved.getId();
    }

    @Transactional
    public void handle(AttachVenueCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

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
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

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
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

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

    private void assertCanModifyEvent(SocialEvent event, Long currentUserId) {
        if (!event.canBeManagedBy(currentUserId)) {
            throw new AccessDeniedException("User is not authorized to modify event: " + event.getId().value());
        }
    }
}
