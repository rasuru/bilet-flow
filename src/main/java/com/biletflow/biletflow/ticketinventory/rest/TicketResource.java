package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.ticketinventory.application.ticket.TicketService;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.ClaimTicketCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Authenticated attendee ticket endpoints.")
public class TicketResource {

    private final TicketService ticketService;

    public TicketResource(TicketService ticketService) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService cannot be null");
    }

    @GetMapping("/mine")
    @Operation(summary = "List my tickets", description = "Returns tickets currently linked to the authenticated user account.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Tickets returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TicketView.class)))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public List<TicketView> getMine() {
        return ticketService.getMine();
    }

    @PostMapping("/{ticketId}/claim")
    @Operation(
        summary = "Claim a ticket",
        description = """
        Links an unowned ticket to the authenticated user.

        The authenticated user's verified email must match the ticket attendee email.
        Repeating the request for a ticket already linked to the same user is idempotent.
        """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Ticket linked to the authenticated user",
            content = @Content(schema = @Schema(implementation = TicketView.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "The user has no verified email or the verified email does not match the ticket attendee email",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Ticket not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Ticket is already linked to another user",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        ),
    })
    public ResponseEntity<TicketView> claim(
        @Parameter(
            description = "Ticket identifier",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        ) @PathVariable UUID ticketId
    ) {
        return ResponseEntity.ok(ticketService.claim(new ClaimTicketCommand(ticketId)));
    }
}
