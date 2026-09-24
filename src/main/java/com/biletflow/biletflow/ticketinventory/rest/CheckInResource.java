package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.ticketinventory.application.ticket.CheckInService;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketValidationResult;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/check-in")
@Tag(name = "Event check-in", description = "Online verification for assigned event administrators and the organizer.")
public class CheckInResource {

    private final CheckInService service;

    public CheckInResource(CheckInService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping("/tickets/{ticketCode}/validation")
    @Operation(summary = "Validate a ticket code for this event")
    public TicketValidationResult validate(@PathVariable UUID eventId, @PathVariable UUID ticketCode) {
        return service.validate(eventId, ticketCode);
    }

    @PostMapping("/tickets/{ticketCode}")
    @Operation(summary = "Check in a valid ticket")
    public TicketView checkIn(@PathVariable UUID eventId, @PathVariable UUID ticketCode) {
        return service.checkIn(eventId, ticketCode);
    }

    @PostMapping("/tickets/{ticketCode}/reverse")
    @Operation(summary = "Reverse an accidental check-in")
    public TicketView reverse(@PathVariable UUID eventId, @PathVariable UUID ticketCode) {
        return service.reverse(eventId, ticketCode);
    }

    @GetMapping("/attendees")
    @Operation(summary = "Search event tickets by attendee email")
    public List<TicketView> search(@PathVariable UUID eventId, @RequestParam String email) {
        return service.search(eventId, email);
    }

    @GetMapping("/counts")
    @Operation(summary = "Get active registration and check-in counts")
    public CheckInService.CheckInCounts counts(@PathVariable UUID eventId) {
        return service.counts(eventId);
    }
}
