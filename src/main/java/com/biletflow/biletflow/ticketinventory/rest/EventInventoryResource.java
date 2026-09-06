package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.EventInventoryService;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.view.EventInventoryView;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.view.GaAvailabilityView;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.view.SeatAvailabilityView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}")
@Tag(name = "Ticket Inventory", description = "Public event inventory and availability endpoints used by attendee-facing clients.")
public class EventInventoryResource {

    private final EventInventoryService inventoryService;

    public EventInventoryResource(EventInventoryService inventoryService) {
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService cannot be null");
    }

    @GetMapping("/inventory")
    @Operation(
        summary = "Get event inventory",
        description = """
        Returns the complete inventory view for an event.

        For GENERAL_ADMISSION events, gaAvailability is populated and seatAvailability is empty.
        For ASSIGNED_SEATING events, seatAvailability is populated and gaAvailability is empty.
        """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Inventory returned",
            content = @Content(schema = @Schema(implementation = EventInventoryView.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No inventory exists for the event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public EventInventoryView getInventory(
        @Parameter(
            description = "Event identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID eventId
    ) {
        return inventoryService.getInventory(eventId);
    }

    @GetMapping("/availability")
    @Operation(
        summary = "Get general-admission availability",
        description = """
        Returns capacity counters for each ticket type in a general-admission event.
        Reserved inventory includes active temporary checkout reservations.
        """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "General-admission availability returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GaAvailabilityView.class)))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No inventory exists for the event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "The event uses assigned seating",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public List<GaAvailabilityView> getGeneralAdmissionAvailability(
        @Parameter(
            description = "Event identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID eventId
    ) {
        return inventoryService.getAvailability(eventId);
    }

    @GetMapping("/seat-map")
    @Operation(
        summary = "Get seat availability",
        description = """
        Returns the current availability status of every seat for an assigned-seating event.
        Possible statuses are AVAILABLE, HELD, and SOLD.
        """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Seat availability returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SeatAvailabilityView.class)))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No inventory exists for the event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "The event uses general admission",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public List<SeatAvailabilityView> getSeatAvailability(
        @Parameter(
            description = "Event identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID eventId
    ) {
        return inventoryService.getSeatMap(eventId);
    }
}
