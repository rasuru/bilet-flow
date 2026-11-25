package com.biletflow.biletflow.eventmanagement.application.socialevent;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.command.*;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.PrivateAccessView;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.eventmanagement.ohs.EventCancelled;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private final SocialEventRepository eventRepository;
    private final VenueLayoutRepository venueLayoutRepository;
    private final EventAuditService auditService;
    private final CurrentActor currentActor;
    private final Clock clock;
    private final TransactionLocks locks;
    private final EventBus events;

    public EventService(
        SocialEventRepository eventRepository,
        VenueLayoutRepository venueLayoutRepository,
        EventAuditService auditService,
        CurrentActor currentActor,
        Clock clock,
        TransactionLocks locks,
        EventBus events
    ) {
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.venueLayoutRepository = Objects.requireNonNull(venueLayoutRepository);
        this.auditService = Objects.requireNonNull(auditService);
        this.currentActor = Objects.requireNonNull(currentActor);
        this.clock = Objects.requireNonNull(clock);
        this.locks = Objects.requireNonNull(locks);
        this.events = Objects.requireNonNull(events);
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

        if (event.getStatus() == SocialEventStatus.CANCELLED) return;
        event.cancel(command.cancellationTime());
        eventRepository.save(event);
        events.publish(new EventCancelled(event.getId().value(), command.cancellationTime()).toEvent());

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

    @Transactional
    public void handle(DefineRefundPolicyCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        RefundPolicy policy = new RefundPolicy(command.refundsAllowed(), command.cutoffHoursBeforeStart(), command.terms());
        event.defineRefundPolicy(policy);
        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.REFUND_POLICY_UPDATED,
            "Refund policy set: " + policy.summary()
        );
    }

    @Transactional
    public void handle(RemoveRefundPolicyCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        if (!event.removeRefundPolicy()) return;
        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.REFUND_POLICY_REMOVED,
            "Refund policy removed"
        );
    }

    @Transactional
    public PrivateAccessView handle(RotatePrivateAccessCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Long currentUserId = currentActor.requireUserId();

        SocialEvent event = requireEvent(command.eventId());
        assertCanModifyEvent(event, currentUserId);

        PrivateAccessCode code = event.rotatePrivateAccessCode();
        eventRepository.save(event);

        auditService.record(
            event.getId().value(),
            currentUserId,
            EventAuditRepository.EventAuditType.PRIVATE_ACCESS_ROTATED,
            "Private access link replaced; the previous link no longer works"
        );
        return new PrivateAccessView(event.getId().value(), code.value());
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
        // Serialize lifecycle changes with checkout issuance and admission.
        locks.lock("event-lifecycle:" + eventId.value());
        return eventRepository.findById(eventId).orElseThrow(() -> new SocialEventNotFoundException(eventId.value().toString()));
    }

    private void assertCanModifyEvent(SocialEvent event, Long currentUserId) {
        if (!event.canBeManagedBy(currentUserId)) {
            throw new AuthorizationException("User is not authorized to modify event: " + event.getId().value());
        }
    }
}
