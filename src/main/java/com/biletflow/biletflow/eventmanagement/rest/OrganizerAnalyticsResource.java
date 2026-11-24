package com.biletflow.biletflow.eventmanagement.rest;

import com.biletflow.biletflow.eventmanagement.application.analytics.OrganizerAnalyticsOverview;
import com.biletflow.biletflow.eventmanagement.application.analytics.OrganizerAnalyticsService;
import com.biletflow.biletflow.eventmanagement.application.analytics.OrganizerAnalyticsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrganizerAnalyticsResource {

    private final OrganizerAnalyticsService analytics;

    public OrganizerAnalyticsResource(OrganizerAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/api/events/mine/analytics")
    @Operation(
        summary = "Get all-time totals across the signed-in organizer’s events",
        description = "Inventory is current. Attendance includes only events that have started."
    )
    @ApiResponse(responseCode = "200", description = "Organizer analytics totals")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    public OrganizerAnalyticsOverview overview() {
        return analytics.overview();
    }

    @GetMapping("/api/events/{eventId}/analytics")
    @Operation(
        summary = "Get organizer analytics for a managed event",
        description = """
        Date bounds are inclusive order dates in Asia/Almaty. Refunds and attendance are current outcomes for those orders.
        Inventory is a current snapshot. Net demonstration revenue is gross sales less discounts and confirmed refunds.
        """
    )
    @ApiResponse(responseCode = "200", description = "Analytics snapshot")
    @ApiResponse(responseCode = "400", description = "Invalid date range or ticket type")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Only the event organizer may view analytics")
    @ApiResponse(responseCode = "404", description = "Event not found")
    public OrganizerAnalyticsView report(
        @PathVariable UUID eventId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through,
        @RequestParam(required = false) UUID ticketTypeId
    ) {
        return analytics.report(eventId, from, through, ticketTypeId);
    }
}
