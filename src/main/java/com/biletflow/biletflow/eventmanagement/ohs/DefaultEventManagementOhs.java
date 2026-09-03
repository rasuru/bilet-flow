package com.biletflow.biletflow.eventmanagement.ohs;

import com.biletflow.biletflow.eventmanagement.domain.*;
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

    public DefaultEventManagementOhs(SocialEventRepository eventRepository, VenueLayoutRepository layoutRepository) {
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.layoutRepository = Objects.requireNonNull(layoutRepository);
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
                    seat.accessible(),
                    seat.priceCategory()
                )
            )
            .toList();

        return new EventSeatMapView(eventId, layout.getId().value(), seats);
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
