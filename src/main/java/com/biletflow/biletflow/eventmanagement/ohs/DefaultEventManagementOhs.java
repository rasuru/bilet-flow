package com.biletflow.biletflow.eventmanagement.ohs;

import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditRepository;
import com.biletflow.biletflow.eventmanagement.application.audit.EventAuditService;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.VenueLayoutNotFoundException;
import com.biletflow.biletflow.eventmanagement.ohs.dto.*;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultEventManagementOhs implements EventManagementOhs {

    private final SocialEventRepository eventRepository;
    private final VenueLayoutRepository layoutRepository;
    private final EventAuditService auditService;

    public DefaultEventManagementOhs(SocialEventRepository eventRepository, VenueLayoutRepository layoutRepository,
                                     EventAuditService auditService) {
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.layoutRepository = Objects.requireNonNull(layoutRepository);
        this.auditService = Objects.requireNonNull(auditService);
    }

    @Override
    public EventTicketingConfigurationView getTicketingConfiguration(UUID eventId) {
        SocialEvent event = requireEvent(eventId);
        Venue venue = requireVenue(event);

        return switch (venue.getSeatingConfig()) {
            case Venue.GeneralAdmission ignored -> new EventTicketingConfigurationView(
                eventId,
                SeatingModeView.GENERAL_ADMISSION,
                Set.of()
            );
            case Venue.AssignedSeating assigned -> {
                VenueLayout layout = requireLayout(assigned.layoutId());

                yield new EventTicketingConfigurationView(
                    eventId,
                    SeatingModeView.ASSIGNED_SEATING,
                    layout
                        .getSeats()
                        .stream()
                        .map(seat -> new TicketingSeatReferenceView(seat.id().value(), seat.priceCategory()))
                        .collect(Collectors.toUnmodifiableSet())
                );
            }
        };
    }

    @Override
    public EventSeatMapView getSeatMap(UUID eventId) {
        SocialEvent event = requireEvent(eventId);
        Venue venue = requireVenue(event);

        if (!(venue.getSeatingConfig() instanceof Venue.AssignedSeating assigned)) {
            throw new IllegalStateException("Event does not use assigned seating: " + eventId);
        }

        VenueLayout layout = requireLayout(assigned.layoutId());

        List<SeatReferenceView> seats = layout
            .getSeats()
            .stream()
            .map(seat ->
                new SeatReferenceView(
                    seat.id().value(),
                    seat.location().section(),
                    seat.location().row(),
                    seat.location().seatNumber(),

                    seat.priceCategory()
                )
            )
            .toList();

        return new EventSeatMapView(eventId, layout.getId().value(), seats);
    }

    @Override
    public boolean canManageTicketing(UUID eventId, Long userId) {
        SocialEvent event = requireEvent(eventId);
        return event.canBeManagedBy(userId);
    }

    @Override
    public boolean canCheckIn(UUID eventId, Long userId) {
        SocialEvent event = requireEvent(eventId);
        return event.getStatus() == SocialEventStatus.PUBLISHED && event.canCheckIn(userId);
    }

    @Override
    public boolean canHandleAttendeeSupport(UUID eventId, Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return eventRepository.findById(new SocialEventId(Objects.requireNonNull(eventId, "eventId cannot be null")))
            .map(event -> event.canHandleSupport(userId))
            .orElse(false);
    }

    @Override
    public List<UUID> findSupportManagedEventIds(Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return java.util.stream.Stream.concat(
                eventRepository.findByOrganizerId(userId).stream(),
                eventRepository.findByStaffUserId(userId).stream())
            .filter(event -> event.canHandleSupport(userId))
            .map(event -> event.getId().value())
            .distinct()
            .toList();
    }

    @Override
    public java.util.Optional<SupportRouting> supportRouting(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return eventRepository.findById(new SocialEventId(eventId)).map(event -> {
            List<Long> staff = event.getStaffAssignments().stream()
                .filter(assignment -> assignment.role() == StaffRole.EVENT_ADMIN)
                .map(StaffAssignment::userId)
                .distinct()
                .toList();
            return new SupportRouting(eventId, event.getOrganizerId(), staff);
        });
    }

    @Override
    @Transactional
    public void recordSupportCaseStatusChanged(UUID eventId, UUID caseId, Long actorUserId,
                                                String previousStatus, String newStatus, java.time.Instant occurredAt) {
        Objects.requireNonNull(caseId, "caseId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        // EventAuditService owns the event activity log; Support supplies only its published audit facts.
        auditService.recordAt(eventId, actorUserId, EventAuditRepository.EventAuditType.SUPPORT_CASE_STATUS_CHANGED,
            "Support case " + caseId + " status changed from " + previousStatus + " to " + newStatus, occurredAt);
    }

    @Override
    public SalesScope salesScope(UUID eventId, java.time.Instant now) {
        SocialEvent event = requireEvent(eventId);
        boolean cancelled = event.getStatus() == SocialEventStatus.CANCELLED;
        boolean open =
            event.getStatus() == SocialEventStatus.PUBLISHED &&
            event.getRegistrationWindow().isOpenAt(now) &&
            !event.getDateRange().hasStarted(now);
        return new SalesScope(
            eventId, event.getOrganizerId(), open, cancelled, event.getDateRange().startAt(),
            event.getVisibility() != EventVisibility.PRIVATE
        );
    }

    @Override
    public TicketEventDetailsView ticketDetails(UUID eventId, UUID seatId) {
        var event = requireEvent(eventId);
        var venue = requireVenue(event);
        String seatLabel = "General admission";
        if (seatId != null) {
            if (!(venue.getSeatingConfig() instanceof Venue.AssignedSeating assigned)) {
                throw new IllegalStateException("Ticket seat does not match the event venue");
            }
            seatLabel = requireLayout(assigned.layoutId())
                .getSeats()
                .stream()
                .filter(seat -> seat.id().value().equals(seatId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Ticket seat is missing from the event layout"))
                .location()
                .toLabel();
        }
        return new TicketEventDetailsView(
            event.getTitle(),
            event.getDateRange().startAt(),
            event.getDateRange().endAt(),
            venue.getName(),
            venue.getAddress(),
            seatLabel,
            event.getStatus() == SocialEventStatus.CANCELLED,
            event.getStatus() == SocialEventStatus.SUSPENDED
        );
    }

    private SocialEvent requireEvent(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");

        return eventRepository.findById(new SocialEventId(eventId)).orElseThrow(() -> new SocialEventNotFoundException(eventId.toString()));
    }

    private Venue requireVenue(SocialEvent event) {
        return event
            .getVenue()
            .orElseThrow(() -> new IllegalStateException("Event does not have a venue configured: " + event.getId().value()));
    }

    private VenueLayout requireLayout(VenueLayoutId layoutId) {
        return layoutRepository.findById(layoutId).orElseThrow(() -> new VenueLayoutNotFoundException(layoutId.value().toString()));
    }
}

