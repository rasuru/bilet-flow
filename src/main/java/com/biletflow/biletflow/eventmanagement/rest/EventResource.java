package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.eventmanagement.application.socialevent.EventQueryService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.EventService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.command.*;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.ManagedEventView;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.PublicEventView;
import com.biletflow.biletflow.eventmanagement.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventResource {

    private final EventService eventService;
    private final EventQueryService eventQueryService;
    private final Clock clock;

    public EventResource(EventService eventService, EventQueryService eventQueryService, Clock clock) {
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.eventQueryService = Objects.requireNonNull(eventQueryService, "eventQueryService cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @GetMapping
    public List<PublicEventView> listPublic() {
        return eventQueryService.listPublic();
    }

    @GetMapping("/{eventId}")
    public PublicEventView getPublic(@PathVariable UUID eventId) {
        return eventQueryService.getPublic(new SocialEventId(eventId));
    }

    @GetMapping("/mine")
    public List<ManagedEventView> listMine() {
        return eventQueryService.listMine();
    }

    @GetMapping("/{eventId}/manage")
    public ManagedEventView getForManagement(@PathVariable UUID eventId) {
        return eventQueryService.getForManagement(new SocialEventId(eventId));
    }

    @PostMapping
    public ResponseEntity<IdResponse> create(@Valid @RequestBody EventDetailsRequest request) {
        SocialEventId id = eventService.handle(
            new CreateDraftEventCommand(
                request.title(),
                request.description(),
                request.category(),
                request.imageUrl(),
                request.visibility(),
                new EventDateRange(request.startAt(), request.endAt()),
                toRegistrationWindow(request.registrationWindow())
            )
        );

        return ResponseEntity.created(URI.create("/api/events/" + id.value() + "/manage")).body(new IdResponse(id.value()));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<Void> update(@PathVariable UUID eventId, @Valid @RequestBody EventDetailsRequest request) {
        eventService.handle(
            new UpdateEventDetailsCommand(
                new SocialEventId(eventId),
                request.title(),
                request.description(),
                request.category(),
                request.imageUrl(),
                request.visibility(),
                new EventDateRange(request.startAt(), request.endAt()),
                toRegistrationWindow(request.registrationWindow())
            )
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/publish")
    public ResponseEntity<Void> publish(@PathVariable UUID eventId) {
        eventService.handle(new PublishSocialEventCommand(new SocialEventId(eventId)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/unpublish")
    public ResponseEntity<Void> unpublish(@PathVariable UUID eventId) {
        eventService.handle(new UnpublishEventCommand(new SocialEventId(eventId)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID eventId) {
        eventService.handle(new CancelSocialEventCommand(new SocialEventId(eventId), Instant.now(clock)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/duplicate")
    public ResponseEntity<IdResponse> duplicate(@PathVariable UUID eventId, @Valid @RequestBody DuplicateEventRequest request) {
        SocialEventId duplicatedId = eventService.handle(
            new DuplicateEventCommand(
                new SocialEventId(eventId),
                new EventDateRange(request.startAt(), request.endAt()),
                toRegistrationWindow(request.registrationWindow())
            )
        );

        return ResponseEntity.created(URI.create("/api/events/" + duplicatedId.value() + "/manage")).body(
            new IdResponse(duplicatedId.value())
        );
    }

    @PostMapping("/{eventId}/venue")
    public ResponseEntity<Void> attachVenue(@PathVariable UUID eventId, @Valid @RequestBody VenueRequest request) {
        eventService.handle(new AttachVenueCommand(new SocialEventId(eventId), toVenue(request)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/staff")
    public ResponseEntity<Void> assignStaff(@PathVariable UUID eventId, @Valid @RequestBody AssignStaffRequest request) {
        eventService.handle(new AssignStaffCommand(new SocialEventId(eventId), request.staffUserId(), request.role()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}/staff/{staffUserId}")
    public ResponseEntity<Void> removeStaff(@PathVariable UUID eventId, @PathVariable Long staffUserId) {
        eventService.handle(new RemoveStaffCommand(new SocialEventId(eventId), staffUserId));
        return ResponseEntity.noContent().build();
    }

    private RegistrationWindow toRegistrationWindow(RegistrationWindowRequest request) {
        return switch (request.mode()) {
            case ALWAYS_OPEN -> new RegistrationWindow.AlwaysOpen();
            case OPENS_AT -> new RegistrationWindow.OpensAt(requireInstant(request.startAt(), "registrationWindow.startAt"));
            case BOUNDED -> new RegistrationWindow.Bounded(
                requireInstant(request.startAt(), "registrationWindow.startAt"),
                requireInstant(request.endAt(), "registrationWindow.endAt")
            );
        };
    }

    private Venue toVenue(VenueRequest request) {
        return switch (request.seatingMode()) {
            case GENERAL_ADMISSION -> Venue.createGeneralAdmission(request.name(), request.address(), request.capacity());
            case ASSIGNED_SEATING -> Venue.createAssignedSeating(
                request.name(),
                request.address(),
                request.capacity(),
                new VenueLayoutId(requireUuid(request.venueLayoutId(), "venueLayoutId"))
            );
        };
    }

    private Instant requireInstant(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private UUID requireUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public record IdResponse(UUID id) {
        public IdResponse {
            Objects.requireNonNull(id, "id cannot be null");
        }
    }

    public record EventDetailsRequest(
        @NotBlank String title,
        String description,
        @NotBlank String category,
        String imageUrl,
        @NotNull EventVisibility visibility,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @Valid @NotNull RegistrationWindowRequest registrationWindow
    ) {}

    public record DuplicateEventRequest(
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @Valid @NotNull RegistrationWindowRequest registrationWindow
    ) {}

    public record RegistrationWindowRequest(@NotNull RegistrationWindowMode mode, Instant startAt, Instant endAt) {}

    public enum RegistrationWindowMode {
        ALWAYS_OPEN,
        OPENS_AT,
        BOUNDED,
    }

    public record VenueRequest(
        @NotBlank String name,
        @NotBlank String address,
        @Positive int capacity,
        @NotNull SeatingMode seatingMode,
        UUID venueLayoutId
    ) {}

    public enum SeatingMode {
        GENERAL_ADMISSION,
        ASSIGNED_SEATING,
    }

    public record AssignStaffRequest(@NotNull Long staffUserId, @NotNull StaffRole role) {}
}
