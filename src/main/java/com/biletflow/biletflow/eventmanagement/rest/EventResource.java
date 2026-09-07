package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.eventmanagement.application.socialevent.EventQueryService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.EventService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.command.*;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.ManagedEventView;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.PublicEventView;
import com.biletflow.biletflow.eventmanagement.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Events", description = "Public event discovery and organizer event-management operations.")
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
    @Operation(
        summary = "List public events",
        description = "Returns published events with PUBLIC visibility, ordered by event start time."
    )
    @ApiResponse(responseCode = "200", description = "Public events returned")
    public List<PublicEventView> listPublic() {
        return eventQueryService.listPublic();
    }

    @GetMapping("/{eventId}")
    @Operation(
        summary = "Get a publicly readable event",
        description = "Returns a PUBLIC or UNLISTED event when its status is PUBLISHED or CANCELLED. " +
            "Private, draft, and unpublished events are exposed as not found."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event returned"),
        @ApiResponse(responseCode = "404", description = "Event does not exist or is not publicly readable", content = @Content),
    })
    public PublicEventView getPublic(@Parameter(description = "Event ID", required = true) @PathVariable UUID eventId) {
        return eventQueryService.getPublic(new SocialEventId(eventId));
    }

    @GetMapping("/mine")
    @Operation(
        summary = "List events owned by the current organizer",
        description = "Returns events for which the authenticated user is the organizer, newest event start first."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Managed events returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
    })
    public List<ManagedEventView> listMine() {
        return eventQueryService.listMine();
    }

    @GetMapping("/{eventId}/manage")
    @Operation(
        summary = "Get an event for organizer management",
        description = "Returns the management view, including staff assignments, to the event organizer."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Management view returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Current user does not manage this event", content = @Content),
        @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
    })
    public ManagedEventView getForManagement(@Parameter(description = "Event ID", required = true) @PathVariable UUID eventId) {
        return eventQueryService.getForManagement(new SocialEventId(eventId));
    }

    @PostMapping
    @Operation(
        summary = "Create a draft event",
        description = "Creates a new DRAFT event owned by the authenticated user. Venue configuration is attached separately."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Draft created"),
        @ApiResponse(responseCode = "400", description = "Request validation or domain validation failed", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
    })
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
    @Operation(summary = "Update event details", description = "Replaces the editable event details. Cancelled events cannot be modified.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Event updated"),
        @ApiResponse(responseCode = "400", description = "Request validation or domain validation failed", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Current user does not manage this event", content = @Content),
        @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Event state rejects the operation", content = @Content),
    })
    public ResponseEntity<Void> update(
        @Parameter(description = "Event ID", required = true) @PathVariable UUID eventId,
        @Valid @RequestBody EventDetailsRequest request
    ) {
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
    @Operation(
        summary = "Publish an event",
        description = "Publishes an organizer-managed event. A venue must already be attached and a cancelled event cannot be published."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Event published"),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Current user does not manage this event", content = @Content),
        @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Event cannot be published in its current state", content = @Content),
    })
    public ResponseEntity<Void> publish(@Parameter(description = "Event ID", required = true) @PathVariable UUID eventId) {
        eventService.handle(new PublishSocialEventCommand(new SocialEventId(eventId)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/unpublish")
    @Operation(
        summary = "Unpublish an event",
        description = "Moves a PUBLISHED event to UNPUBLISHED. Only currently published events can be unpublished."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Event unpublished"),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Current user does not manage this event", content = @Content),
        @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Event is not currently published", content = @Content),
    })
    public ResponseEntity<Void> unpublish(@Parameter(description = "Event ID", required = true) @PathVariable UUID eventId) {
        eventService.handle(new UnpublishEventCommand(new SocialEventId(eventId)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/cancel")
    @Operation(
        summary = "Cancel an event",
        description = "Cancels the event at the server's current time. An event that has already ended cannot be cancelled."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Event cancelled"),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Current user does not manage this event", content = @Content),
        @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Event has already ended", content = @Content),
    })
    public ResponseEntity<Void> cancel(@Parameter(description = "Event ID", required = true) @PathVariable UUID eventId) {
        eventService.handle(new CancelSocialEventCommand(new SocialEventId(eventId), Instant.now(clock)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/duplicate")
    @Operation(
        summary = "Duplicate an event into a new draft",
        description = "Copies reusable event configuration into a new DRAFT with a new date range and registration window. " +
            "Staff assignments are not copied."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Duplicate draft created"),
        @ApiResponse(responseCode = "400", description = "Request validation or domain validation failed", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Current user does not manage the source event", content = @Content),
        @ApiResponse(responseCode = "404", description = "Source event not found", content = @Content),
    })
    public ResponseEntity<IdResponse> duplicate(
        @Parameter(description = "Source event ID", required = true) @PathVariable UUID eventId,
        @Valid @RequestBody DuplicateEventRequest request
    ) {
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
    @Operation(
        summary = "Attach venue and seating configuration",
        description = "Attaches the event's immutable venue configuration. This is allowed only while the event is a DRAFT " +
            "and only once. ASSIGNED_SEATING requires venueLayoutId; capacity cannot exceed the referenced layout size."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Venue attached"),
        @ApiResponse(responseCode = "400", description = "Invalid venue configuration or unknown venue layout", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Current user does not manage this event", content = @Content),
        @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Venue cannot be attached in the event's current state", content = @Content),
    })
    public ResponseEntity<Void> attachVenue(
        @Parameter(description = "Event ID", required = true) @PathVariable UUID eventId,
        @Valid @RequestBody VenueRequest request
    ) {
        eventService.handle(new AttachVenueCommand(new SocialEventId(eventId), toVenue(request)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/staff")
    @Operation(
        summary = "Assign event staff",
        description = "Assigns a user to the event with the requested staff role. Repeating the same assignment is idempotent."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Staff assignment present"),
        @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Current user does not manage this event", content = @Content),
        @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Cancelled event cannot be modified", content = @Content),
    })
    public ResponseEntity<Void> assignStaff(
        @Parameter(description = "Event ID", required = true) @PathVariable UUID eventId,
        @Valid @RequestBody AssignStaffRequest request
    ) {
        eventService.handle(new AssignStaffCommand(new SocialEventId(eventId), request.staffUserId(), request.role()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}/staff/{staffUserId}")
    @Operation(summary = "Remove event staff", description = "Removes all staff assignments for the supplied user from the event.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Staff assignment absent"),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Current user does not manage this event", content = @Content),
        @ApiResponse(responseCode = "404", description = "Event not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Cancelled event cannot be modified", content = @Content),
    })
    public ResponseEntity<Void> removeStaff(
        @Parameter(description = "Event ID", required = true) @PathVariable UUID eventId,
        @Parameter(description = "User ID of the staff member to remove", required = true) @PathVariable Long staffUserId
    ) {
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

    @Schema(description = "Identifier returned after creating an event resource.")
    public record IdResponse(@Schema(description = "Created event ID", example = "7d24db20-2e37-4ac3-a781-cf6725cab5ef") UUID id) {
        public IdResponse {
            Objects.requireNonNull(id, "id cannot be null");
        }
    }

    @Schema(description = "Editable event details used when creating or updating an event.")
    public record EventDetailsRequest(
        @Schema(description = "Event title", example = "Astana Tech Meetup") @NotBlank String title,
        @Schema(description = "Event description", example = "An evening of talks and networking.") String description,
        @Schema(description = "Event category", example = "Technology") @NotBlank String category,
        @Schema(description = "Optional event image URL", example = "https://example.com/events/tech-meetup.jpg") String imageUrl,
        @Schema(description = "Discovery visibility. UNLISTED events are readable by direct link; PRIVATE events are not public.")
        @NotNull
        EventVisibility visibility,
        @Schema(description = "Event start time as an ISO-8601 instant", example = "2026-10-15T13:00:00Z") @NotNull Instant startAt,
        @Schema(description = "Event end time; must be strictly after startAt", example = "2026-10-15T17:00:00Z") @NotNull Instant endAt,
        @Valid @NotNull RegistrationWindowRequest registrationWindow
    ) {}

    @Schema(description = "New scheduling data for a duplicated event.")
    public record DuplicateEventRequest(
        @Schema(description = "Start time for the new draft", example = "2026-11-20T13:00:00Z") @NotNull Instant startAt,
        @Schema(description = "End time for the new draft; must be after startAt", example = "2026-11-20T17:00:00Z") @NotNull Instant endAt,
        @Valid @NotNull RegistrationWindowRequest registrationWindow
    ) {}

    @Schema(
        description = "Ticket-registration availability. ALWAYS_OPEN ignores startAt/endAt; OPENS_AT requires startAt; " +
            "BOUNDED requires both startAt and endAt."
    )
    public record RegistrationWindowRequest(
        @Schema(description = "Registration-window mode") @NotNull RegistrationWindowMode mode,
        @Schema(description = "Required for OPENS_AT and BOUNDED", example = "2026-09-15T06:00:00Z") Instant startAt,
        @Schema(description = "Required for BOUNDED; must not be before startAt", example = "2026-10-15T12:00:00Z") Instant endAt
    ) {}

    @Schema(description = "How registration availability is scheduled.")
    public enum RegistrationWindowMode {
        ALWAYS_OPEN,
        OPENS_AT,
        BOUNDED,
    }

    @Schema(description = "Venue configuration attached to a draft event. venueLayoutId is required only for ASSIGNED_SEATING.")
    public record VenueRequest(
        @Schema(description = "Venue name", example = "Astana Hub") @NotBlank String name,
        @Schema(description = "Human-readable venue address", example = "Mangilik El Ave 55/8, Astana") @NotBlank String address,
        @Schema(description = "Maximum event capacity", example = "250") @Positive int capacity,
        @Schema(description = "General-admission or assigned-seating event") @NotNull SeatingMode seatingMode,
        @Schema(
            description = "Predefined venue layout ID. Required for ASSIGNED_SEATING and ignored for GENERAL_ADMISSION.",
            example = "62965c40-ab0a-42ba-976f-d85b74d7d03a"
        )
        UUID venueLayoutId
    ) {}

    @Schema(description = "Seating model used by the event.")
    public enum SeatingMode {
        GENERAL_ADMISSION,
        ASSIGNED_SEATING,
    }

    @Schema(description = "Event staff assignment request.")
    public record AssignStaffRequest(
        @Schema(description = "User ID to assign", example = "42") @NotNull Long staffUserId,
        @Schema(description = "Role granted for this event") @NotNull StaffRole role
    ) {}
}
