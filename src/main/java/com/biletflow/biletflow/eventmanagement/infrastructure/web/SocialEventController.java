package com.biletflow.biletflow.eventmanagement.infrastructure.web;

import com.biletflow.biletflow.eventmanagement.application.*;
import com.biletflow.biletflow.eventmanagement.application.EventCommandService;
import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.infrastructure.web.dto.CreateSocialEventRequest;
import com.biletflow.biletflow.eventmanagement.infrastructure.web.dto.CreateTicketTypeRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class SocialEventController {

    private final EventCommandService commandService;

    public SocialEventController(EventCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    public ResponseEntity<UUID> createEvent(@Valid @RequestBody CreateSocialEventRequest request) {
        SocialEventId id = commandService.handle(
            new CreateDraftEventCommand(
                request.title(),
                request.description(),
                request.category(),
                request.imageUrl(),
                EventVisibility.valueOf(request.visibility()),
                new EventDateRange(request.startAt(), request.endAt()),
                mapRegistrationWindow(request.regWindowType(), request.regWindowStart(), request.regWindowEnd())
            )
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(id.value());
    }

    @PostMapping("/{eventId}/publish")
    public ResponseEntity<Void> publishEvent(@PathVariable UUID eventId) {
        commandService.handle(new PublishSocialEventCommand(new SocialEventId(eventId)));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<Void> cancelEvent(@PathVariable UUID eventId) {
        commandService.handle(new CancelSocialEventCommand(new SocialEventId(eventId), Instant.now()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{eventId}/ticket-types")
    public ResponseEntity<Void> createTicketType(@PathVariable UUID eventId, @Valid @RequestBody CreateTicketTypeRequest request) {
        TicketPricing pricing = "PAID".equalsIgnoreCase(request.pricingType())
            ? new TicketPricing.Paid(request.priceAmount(), request.priceCurrency())
            : new TicketPricing.Free();

        commandService.handle(
            new CreateTicketTypeCommand(
                new SocialEventId(eventId),
                request.name(),
                request.description(),
                pricing,
                request.totalQuantity(),
                new SalesWindow(request.salesStart(), request.salesEnd()),
                request.maxPerOrder()
            )
        );

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/{eventId}/ticket-types/{ticketTypeId}/hide")
    public ResponseEntity<Void> hideTicketType(@PathVariable UUID eventId, @PathVariable UUID ticketTypeId) {
        commandService.handle(new HideTicketTypeCommand(new SocialEventId(eventId), new TicketTypeId(ticketTypeId)));
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{eventId}/ticket-types/{ticketTypeId}/reveal")
    public ResponseEntity<Void> revealTicketType(@PathVariable UUID eventId, @PathVariable UUID ticketTypeId) {
        commandService.handle(new RevealTicketTypeCommand(new SocialEventId(eventId), new TicketTypeId(ticketTypeId)));
        return ResponseEntity.ok().build();
    }

    private RegistrationWindow mapRegistrationWindow(String type, Instant start, Instant end) {
        return switch (type.toUpperCase()) {
            case "OPENS_AT" -> new RegistrationWindow.OpensAt(start);
            case "BOUNDED" -> new RegistrationWindow.Bounded(start, end);
            default -> new RegistrationWindow.AlwaysOpen();
        };
    }
}
