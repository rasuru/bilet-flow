package com.biletflow.biletflow.organizerhistory.rest;

import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.application.HistoryPage;
import com.biletflow.biletflow.organizerhistory.application.HistoryQuery;
import com.biletflow.biletflow.organizerhistory.application.OrganizerHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/history")
@Tag(name = "Organizer history", description = "Read-only activity timeline of an event.")
@ApiResponses({
    @ApiResponse(
        responseCode = "400",
        description = "Invalid filter",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "403",
        description = "Caller is not the event's organizer and is not an administrator",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Event not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
})
public class OrganizerHistoryResource {

    private final OrganizerHistoryService history;

    public OrganizerHistoryResource(OrganizerHistoryService history) {
        this.history = Objects.requireNonNull(history);
    }

    @GetMapping
    @Operation(
        summary = "Get the event activity timeline",
        description = "Newest first. Combines event changes, ticket price and capacity changes, check-ins and reversals, promo campaigns and codes, refunds, paid-sales actions and support cases. from is inclusive and to is exclusive (ISO-8601 instants). category and action may be repeated. Only the event's organizer and platform administrators may read it; assigned event admins may not. Pages are limited to the newest " +
            HistoryQuery.MAX_DEPTH +
            " matching entries. Entries cannot be edited or deleted."
    )
    @ApiResponse(
        responseCode = "200",
        description = "One page of history",
        content = @Content(schema = @Schema(implementation = HistoryPage.class))
    )
    public ResponseEntity<HistoryPage> history(
        @PathVariable UUID eventId,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(name = "category", required = false) Set<HistoryCategory> categories,
        @RequestParam(name = "action", required = false) Set<String> actions,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + HistoryQuery.DEFAULT_SIZE) int size
    ) {
        var result = history.history(new HistoryQuery(eventId, from, to, categories, actions, page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
