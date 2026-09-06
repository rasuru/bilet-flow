package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.application.tickettype.TicketTypeService;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.CreateAssignedSeatingTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.CreateGeneralAdmissionTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.HideTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.UnhideTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.UpdateAssignedSeatingTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.UpdateGeneralAdmissionTicketTypeCommand;
import com.biletflow.biletflow.ticketinventory.application.tickettype.view.TicketTypeView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Ticket Types", description = "Public ticket-type discovery and organizer ticket-type management.")
public class TicketTypeResource {

    private final TicketTypeService ticketTypeService;

    public TicketTypeResource(TicketTypeService ticketTypeService) {
        this.ticketTypeService = Objects.requireNonNull(ticketTypeService, "ticketTypeService cannot be null");
    }

    @GetMapping("/events/{eventId}/ticket-types")
    @Operation(
        summary = "List public ticket types",
        description = "Returns ticket types visible to attendees. Hidden ticket types are excluded."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Public ticket types returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = TicketTypeView.class)))
    )
    public List<TicketTypeView> listPublicForEvent(
        @Parameter(
            description = "Event identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID eventId
    ) {
        return ticketTypeService.listPublicForEvent(eventId);
    }

    @GetMapping("/events/{eventId}/ticket-types/manage")
    @Operation(
        summary = "List ticket types for management",
        description = "Returns all ticket types for an event, including hidden types. Requires permission to manage ticketing for the event."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Ticket types returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TicketTypeView.class)))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "User may not manage ticketing for this event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public List<TicketTypeView> listForManagement(
        @Parameter(
            description = "Event identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID eventId
    ) {
        return ticketTypeService.listForManagement(eventId);
    }

    @PostMapping("/events/{eventId}/ticket-types/general-admission")
    @Operation(
        summary = "Create a general-admission ticket type",
        description = """
        Creates a ticket type with its own capacity for a general-admission event.

        The event's inventory mode is obtained from Event Management when inventory is first initialized.
        maxPerOrder may not exceed capacity.
        """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Ticket type created",
            content = @Content(schema = @Schema(implementation = IdResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Request validation or ticket-type constraints failed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "User may not manage ticketing for this event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "The event uses assigned seating or another state rule rejects the operation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public ResponseEntity<IdResponse> createGeneralAdmission(
        @Parameter(
            description = "Event identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID eventId,
        @Valid @RequestBody GeneralAdmissionTicketTypeRequest request
    ) {
        UUID id = ticketTypeService.create(
            new CreateGeneralAdmissionTicketTypeCommand(
                eventId,
                request.name(),
                request.description(),
                Money.kzt(request.price()),
                request.capacity(),
                request.maxPerOrder(),
                request.salesStartAt(),
                request.salesEndAt()
            )
        );

        return ResponseEntity.created(URI.create("/api/events/" + eventId + "/ticket-types/manage")).body(new IdResponse(id));
    }

    @PostMapping("/events/{eventId}/ticket-types/assigned-seating")
    @Operation(
        summary = "Create an assigned-seating ticket type",
        description = """
        Creates a ticket type for one seat price category in an assigned-seating event.

        The price category must exist in the event's seat configuration and maxPerOrder
        may not exceed the number of seats available in the assigned-seating inventory.
        """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Ticket type created",
            content = @Content(schema = @Schema(implementation = IdResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Request validation or ticket-type constraints failed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "User may not manage ticketing for this event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "The event uses general admission or another state rule rejects the operation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public ResponseEntity<IdResponse> createAssignedSeating(
        @Parameter(
            description = "Event identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID eventId,
        @Valid @RequestBody AssignedSeatingTicketTypeRequest request
    ) {
        UUID id = ticketTypeService.create(
            new CreateAssignedSeatingTicketTypeCommand(
                eventId,
                request.name(),
                request.description(),
                Money.kzt(request.price()),
                request.priceCategory(),
                request.maxPerOrder(),
                request.salesStartAt(),
                request.salesEndAt()
            )
        );

        return ResponseEntity.created(URI.create("/api/events/" + eventId + "/ticket-types/manage")).body(new IdResponse(id));
    }

    @PutMapping("/ticket-types/{ticketTypeId}/general-admission")
    @Operation(
        summary = "Update a general-admission ticket type",
        description = """
        Replaces the editable ticket-type details and configured capacity.

        Capacity cannot be reduced below currently reserved plus sold inventory,
        and maxPerOrder may not exceed capacity.
        """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Ticket type updated"),
        @ApiResponse(
            responseCode = "400",
            description = "Request validation or capacity constraints failed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "User may not manage ticketing for the event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Ticket type not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Ticket type or event inventory mode is incompatible with this operation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public ResponseEntity<Void> updateGeneralAdmission(
        @Parameter(
            description = "Ticket type identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID ticketTypeId,
        @Valid @RequestBody GeneralAdmissionTicketTypeRequest request
    ) {
        ticketTypeService.update(
            new UpdateGeneralAdmissionTicketTypeCommand(
                ticketTypeId,
                request.name(),
                request.description(),
                Money.kzt(request.price()),
                request.capacity(),
                request.maxPerOrder(),
                request.salesStartAt(),
                request.salesEndAt()
            )
        );

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/ticket-types/{ticketTypeId}/assigned-seating")
    @Operation(
        summary = "Update an assigned-seating ticket type",
        description = """
        Replaces editable details of an assigned-seating ticket type.
        Its price category is fixed after creation.
        """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Ticket type updated"),
        @ApiResponse(
            responseCode = "400",
            description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "User may not manage ticketing for the event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Ticket type not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Ticket type or event inventory mode is incompatible with this operation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public ResponseEntity<Void> updateAssignedSeating(
        @Parameter(
            description = "Ticket type identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID ticketTypeId,
        @Valid @RequestBody AssignedSeatingTicketTypeRequest request
    ) {
        ticketTypeService.update(
            new UpdateAssignedSeatingTicketTypeCommand(
                ticketTypeId,
                request.name(),
                request.description(),
                Money.kzt(request.price()),
                request.maxPerOrder(),
                request.salesStartAt(),
                request.salesEndAt()
            )
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ticket-types/{ticketTypeId}/hide")
    @Operation(
        summary = "Hide a ticket type",
        description = "Hides the ticket type from the public event ticket-type listing without deleting it."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Ticket type hidden"),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "User may not manage ticketing for the event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Ticket type not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Ticket type cannot be modified in its current state",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public ResponseEntity<Void> hide(
        @Parameter(
            description = "Ticket type identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID ticketTypeId
    ) {
        ticketTypeService.hide(new HideTicketTypeCommand(ticketTypeId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ticket-types/{ticketTypeId}/unhide")
    @Operation(summary = "Unhide a ticket type", description = "Makes a hidden ticket type public again. The ticket type becomes ACTIVE.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Ticket type unhidden"),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "User may not manage ticketing for the event",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Ticket type not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Ticket type is not hidden or cannot be modified in its current state",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public ResponseEntity<Void> unhide(
        @Parameter(
            description = "Ticket type identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID ticketTypeId
    ) {
        ticketTypeService.unhide(new UnhideTicketTypeCommand(ticketTypeId));
        return ResponseEntity.noContent().build();
    }

    @Schema(description = "Identifier returned after creating a resource.")
    public record IdResponse(
        @Schema(description = "Created resource identifier", example = "550e8400-e29b-41d4-a716-446655440000") UUID id
    ) {
        public IdResponse {
            Objects.requireNonNull(id, "id cannot be null");
        }
    }

    @Schema(description = "Payload for creating or updating a general-admission ticket type.")
    public record GeneralAdmissionTicketTypeRequest(
        @Schema(description = "Display name", example = "General Admission") @NotBlank String name,

        @Schema(description = "Description shown to attendees", example = "Standard admission ticket") @NotNull String description,

        @Schema(description = "Price in KZT. Use 0 for a free ticket.", example = "5000.00", minimum = "0")
        @NotNull
        @DecimalMin(value = "0", inclusive = true)
        BigDecimal price,

        @Schema(description = "Total inventory capacity for this ticket type", example = "200", minimum = "1") @Positive int capacity,

        @Schema(description = "Maximum quantity allowed in one order", example = "5", minimum = "1") @Positive int maxPerOrder,

        @Schema(description = "UTC instant when sales open", example = "2026-09-10T04:00:00Z") @NotNull Instant salesStartAt,

        @Schema(description = "UTC instant when sales close", example = "2026-10-10T16:00:00Z") @NotNull Instant salesEndAt
    ) {}

    @Schema(description = "Payload for creating or updating an assigned-seating ticket type.")
    public record AssignedSeatingTicketTypeRequest(
        @Schema(description = "Display name", example = "VIP") @NotBlank String name,

        @Schema(description = "Description shown to attendees", example = "VIP seating") @NotNull String description,

        @Schema(description = "Price in KZT. Use 0 for a free ticket.", example = "12000.00", minimum = "0")
        @NotNull
        @DecimalMin(value = "0", inclusive = true)
        BigDecimal price,

        @Schema(
            description = "Seat price category configured by Event Management. It is fixed after ticket-type creation.",
            example = "VIP"
        )
        @NotBlank
        String priceCategory,

        @Schema(description = "Maximum number of seats allowed in one order", example = "4", minimum = "1") @Positive int maxPerOrder,

        @Schema(description = "UTC instant when sales open", example = "2026-09-10T04:00:00Z") @NotNull Instant salesStartAt,

        @Schema(description = "UTC instant when sales close", example = "2026-10-10T16:00:00Z") @NotNull Instant salesEndAt
    ) {}
}
