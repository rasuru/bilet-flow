package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.eventdiscovery.application.EventDiscoveryService;
import com.biletflow.biletflow.eventdiscovery.application.EventDiscoveryService.DiscoveryPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events")
public class EventDiscoveryResource {

    private final EventDiscoveryService discovery;

    public EventDiscoveryResource(EventDiscoveryService discovery) {
        this.discovery = Objects.requireNonNull(discovery);
    }

    @GetMapping
    @Operation(
        summary = "Discover public events",
        description = "Lists published public events that have not ended and whose registration deadline has not passed. " +
            "Includes sold-out events and upcoming sales by default."
    )
    @ApiResponse(responseCode = "200", description = "Page of event summaries")
    @ApiResponse(responseCode = "400", description = "Invalid discovery parameters")
    public DiscoveryPage discover(
        @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size from 1 to 100") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Case-insensitive substring in the title or description") @RequestParam(defaultValue = "") String query,
        @Parameter(description = "Case-insensitive exact category") @RequestParam(defaultValue = "") String category,
        @Parameter(description = "Require an available ticket that can be purchased now")
        @RequestParam(defaultValue = "false") boolean availableOnly,
        @Parameter(description = "Event start date order, startAt,asc or startAt,desc")
        @RequestParam(defaultValue = "startAt,asc") String sort
    ) {
        boolean descending = switch (sort) {
            case "startAt,asc" -> false;
            case "startAt,desc" -> true;
            default -> throw new IllegalArgumentException("sort must be startAt,asc or startAt,desc");
        };
        return discovery.discover(page, size, query, category, availableOnly, descending);
    }
}
