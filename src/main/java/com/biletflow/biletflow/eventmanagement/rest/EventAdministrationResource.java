package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.eventmanagement.application.socialevent.*;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.ManagedEventView;
import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventStatus;
import com.biletflow.biletflow.eventmanagement.domain.report.EventReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Event administration")
@ApiResponse(
    responseCode = "400",
    description = "Invalid request",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
)
@ApiResponse(
    responseCode = "401",
    description = "Authentication required",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
)
@ApiResponse(
    responseCode = "403",
    description = "Administrator access required",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
)
public class EventAdministrationResource {

    private final EventAdministrationService events;
    private final EventReportService reports;

    public EventAdministrationResource(EventAdministrationService events, EventReportService reports) {
        this.events = events;
        this.reports = reports;
    }

    @GetMapping("/events")
    @Operation(
        summary = "Search events across all lifecycle states and visibilities",
        description = "Search matches title or category. Results are ordered by event start, newest first."
    )
    public ResponseEntity<List<EventAdminQueries.Summary>> events(
        @RequestParam(required = false) @Size(max = 255) String search,
        @RequestParam(required = false) SocialEventStatus status,
        @RequestParam(required = false) EventVisibility visibility,
        @RequestParam(required = false) Long organizerId,
        @RequestParam(defaultValue = "0") @Min(0) int offset,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return response(events.search(new EventAdminQueries.Filter(search, status, visibility, organizerId), offset, limit));
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "Get an event for moderation")
    @ApiResponse(responseCode = "404", description = "Event not found")
    public ResponseEntity<ManagedEventView> event(@PathVariable UUID eventId) {
        return response(events.get(eventId));
    }

    public record SuspendRequest(@NotBlank @Size(max = 200) String reason) {}

    @PostMapping("/events/{eventId}/suspend")
    @Operation(
        summary = "Suspend an event",
        description = "Hides the event and stops checkout and check-in. Existing issued tickets keep their state. " +
            "Unfulfilled payments follow checkout compensation rules."
    )
    @ApiResponse(responseCode = "404", description = "Event not found")
    @ApiResponse(responseCode = "409", description = "Event is cancelled or changed concurrently")
    public ResponseEntity<ManagedEventView> suspend(@PathVariable UUID eventId, @Valid @RequestBody SuspendRequest request) {
        return response(events.suspend(eventId, request.reason()));
    }

    @PostMapping("/events/{eventId}/reinstate")
    @Operation(summary = "Restore the event's status from before suspension")
    @ApiResponse(responseCode = "404", description = "Event not found")
    public ResponseEntity<ManagedEventView> reinstate(@PathVariable UUID eventId) {
        return response(events.reinstate(eventId));
    }

    @GetMapping("/event-reports")
    @Operation(summary = "List event reports", description = "Filter by OPEN to find reports awaiting review. Newest reports first.")
    public ResponseEntity<List<EventReportQueries.View>> reports(
        @RequestParam(required = false) UUID eventId,
        @RequestParam(required = false) EventReport.Status status,
        @RequestParam(defaultValue = "0") @Min(0) int offset,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return response(reports.search(eventId, status, offset, limit));
    }

    @GetMapping("/event-reports/{reportId}")
    @Operation(summary = "Get one event report and its current review decision")
    @ApiResponse(responseCode = "404", description = "Report not found")
    public ResponseEntity<EventReportQueries.View> report(@PathVariable UUID reportId) {
        return response(reports.get(reportId));
    }

    public record ReviewRequest(@NotNull EventReport.Decision decision, @NotBlank @Size(max = 1000) String note) {}

    @PostMapping("/event-reports/{reportId}/review")
    @Operation(
        summary = "Confirm or dismiss an event report",
        description = "Stores the review decision, note and reviewer. Event suspension is a separate action."
    )
    @ApiResponse(responseCode = "404", description = "Report not found")
    @ApiResponse(responseCode = "409", description = "Report has already been reviewed with a different decision")
    public ResponseEntity<EventReportQueries.View> review(@PathVariable UUID reportId, @Valid @RequestBody ReviewRequest request) {
        return response(reports.review(reportId, request.decision(), request.note()));
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
