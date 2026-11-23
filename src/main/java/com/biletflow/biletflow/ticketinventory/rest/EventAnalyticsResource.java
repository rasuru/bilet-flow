package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.ticketinventory.application.analytics.EventAnalyticsService;
import com.biletflow.biletflow.ticketinventory.application.analytics.EventAnalyticsView;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/analytics")
public class EventAnalyticsResource {
    private final EventAnalyticsService analytics;

    public EventAnalyticsResource(EventAnalyticsService analytics) {
        this.analytics = Objects.requireNonNull(analytics);
    }

    @GetMapping
    public EventAnalyticsView get(
        @PathVariable UUID eventId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return analytics.get(eventId, from, to);
    }
}
