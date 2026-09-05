package com.biletflow.biletflow.eventmanagement.application.socialevent;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.EventVenueView;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.ManagedEventView;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.PublicEventView;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.RegistrationWindowView;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.SocialEventNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventQueryService {

    private final SocialEventRepository repository;
    private final CurrentActor currentActor;

    public EventQueryService(SocialEventRepository repository, CurrentActor currentActor) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.currentActor = Objects.requireNonNull(currentActor, "currentActor cannot be null");
    }

    public List<PublicEventView> listPublic() {
        return repository
            .findByStatusAndVisibility(SocialEventStatus.PUBLISHED, EventVisibility.PUBLIC)
            .stream()
            .sorted(Comparator.comparing(event -> event.getDateRange().startAt()))
            .map(this::toPublicView)
            .toList();
    }

    public PublicEventView getPublic(SocialEventId eventId) {
        SocialEvent event = requireEvent(eventId);

        boolean publiclyReadableStatus =
            event.getStatus() == SocialEventStatus.PUBLISHED || event.getStatus() == SocialEventStatus.CANCELLED;
        boolean publiclyReadableVisibility =
            event.getVisibility() == EventVisibility.PUBLIC || event.getVisibility() == EventVisibility.UNLISTED;

        if (!publiclyReadableStatus || !publiclyReadableVisibility) {
            throw new SocialEventNotFoundException(eventId.value().toString());
        }

        return toPublicView(event);
    }

    public List<ManagedEventView> listMine() {
        Long currentUserId = currentActor.requireUserId();

        return repository
            .findByOrganizerId(currentUserId)
            .stream()
            .sorted(Comparator.comparing((SocialEvent event) -> event.getDateRange().startAt()).reversed())
            .map(this::toManagedView)
            .toList();
    }

    public ManagedEventView getForManagement(SocialEventId eventId) {
        Long currentUserId = currentActor.requireUserId();
        SocialEvent event = requireEvent(eventId);

        if (!event.canBeManagedBy(currentUserId)) {
            throw new AuthorizationException("User is not authorized to manage event: " + eventId.value());
        }

        return toManagedView(event);
    }

    private SocialEvent requireEvent(SocialEventId eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return repository.findById(eventId).orElseThrow(() -> new SocialEventNotFoundException(eventId.value().toString()));
    }

    private PublicEventView toPublicView(SocialEvent event) {
        return new PublicEventView(
            event.getId().value(),
            event.getTitle(),
            event.getDescription(),
            event.getCategory(),
            event.getImageUrl(),
            event.getStatus(),
            event.getVisibility(),
            event.getDateRange().startAt(),
            event.getDateRange().endAt(),
            toRegistrationWindowView(event.getRegistrationWindow()),
            event.getVenue().map(this::toVenueView).orElse(null)
        );
    }

    private ManagedEventView toManagedView(SocialEvent event) {
        return new ManagedEventView(
            event.getId().value(),
            event.getTitle(),
            event.getDescription(),
            event.getCategory(),
            event.getImageUrl(),
            event.getStatus(),
            event.getVisibility(),
            event.getDateRange().startAt(),
            event.getDateRange().endAt(),
            toRegistrationWindowView(event.getRegistrationWindow()),
            event.getVenue().map(this::toVenueView).orElse(null),
            event
                .getStaffAssignments()
                .stream()
                .map(assignment -> new ManagedEventView.StaffView(assignment.userId(), assignment.role(), assignment.assignedAt()))
                .toList()
        );
    }

    private RegistrationWindowView toRegistrationWindowView(RegistrationWindow window) {
        return switch (window) {
            case RegistrationWindow.AlwaysOpen ignored -> new RegistrationWindowView(RegistrationWindowView.Mode.ALWAYS_OPEN, null, null);
            case RegistrationWindow.OpensAt opensAt -> new RegistrationWindowView(
                RegistrationWindowView.Mode.OPENS_AT,
                opensAt.start(),
                null
            );
            case RegistrationWindow.Bounded bounded -> new RegistrationWindowView(
                RegistrationWindowView.Mode.BOUNDED,
                bounded.start(),
                bounded.end()
            );
        };
    }

    private EventVenueView toVenueView(Venue venue) {
        return switch (venue.getSeatingConfig()) {
            case Venue.GeneralAdmission ignored -> new EventVenueView(
                venue.getName(),
                venue.getAddress(),
                venue.getTotalCapacity(),
                EventVenueView.SeatingMode.GENERAL_ADMISSION,
                null
            );
            case Venue.AssignedSeating assigned -> new EventVenueView(
                venue.getName(),
                venue.getAddress(),
                venue.getTotalCapacity(),
                EventVenueView.SeatingMode.ASSIGNED_SEATING,
                assigned.layoutId().value()
            );
        };
    }
}
