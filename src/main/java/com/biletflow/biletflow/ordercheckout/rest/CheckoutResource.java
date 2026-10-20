package com.biletflow.biletflow.ordercheckout.rest;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ordercheckout.application.CheckoutService;
import com.biletflow.biletflow.ordercheckout.application.command.CreateCheckoutCommand;
import com.biletflow.biletflow.ordercheckout.application.view.CheckoutView;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkouts")
@Tag(name = "Checkout", description = "Ticket reservations, orders, cancellations and full refunds.")
@ApiResponses({
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "403",
        description = "Purchase is private or organizer permission is required",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Checkout, event or inventory not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Purchase state or idempotency conflict",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
})
public class CheckoutResource {

    private final CheckoutService checkout;
    private final CurrentActor actor;

    public CheckoutResource(CheckoutService checkout, CurrentActor actor) {
        this.checkout = Objects.requireNonNull(checkout);
        this.actor = Objects.requireNonNull(actor);
    }

    public record SelectionRequest(
        @NotNull UUID ticketTypeId,
        @NotNull @Size(max = 100) List<@NotNull UUID> seats,
        @NotNull @Size(min = 1, max = 100) List<@NotBlank @Email String> attendeeEmails
    ) {
        CheckoutInventoryOhs.Selection toSelection() {
            return new CheckoutInventoryOhs.Selection(ticketTypeId, seats, attendeeEmails);
        }
    }

    public record CheckoutRequest(
        @NotNull UUID eventId,
        @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid SelectionRequest> selections
    ) {
        CreateCheckoutCommand toCommand() {
            return new CreateCheckoutCommand(eventId, selections.stream().map(SelectionRequest::toSelection).toList());
        }
    }

    public record RefundRequest(@NotBlank @Size(max = 200) String reason) {}

    @PostMapping
    @Operation(
        summary = "Reserve tickets and start checkout",
        description = "Prices come from inventory. Use a new UUID idempotency key for each purchase and reuse that key with identical input after a timeout. Free purchases complete immediately; paid purchases return a payment ID."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Original or newly created checkout",
        content = @Content(schema = @Schema(implementation = CheckoutView.class))
    )
    public ResponseEntity<CheckoutView> create(@RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody CheckoutRequest request) {
        return response(checkout.create(key, actor.requireUserId(), request.toCommand()));
    }

    @GetMapping
    @Operation(summary = "List the current attendee's checkouts")
    @ApiResponse(
        responseCode = "200",
        description = "Purchases, including an empty list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = CheckoutView.class)))
    )
    public ResponseEntity<List<CheckoutView>> mine() {
        return response(checkout.mine(actor.requireUserId()));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get a checkout and its issued tickets",
        description = "Available to the purchaser and authorized event organizers. Tickets appear after verified capture and fulfillment."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Current checkout or order",
        content = @Content(schema = @Schema(implementation = CheckoutView.class))
    )
    public ResponseEntity<CheckoutView> get(@PathVariable UUID id) {
        return response(checkout.get(id, actor.requireUserId()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(
        summary = "Cancel an unfinished checkout",
        description = "Releases holds and abandons the payment. A late capture is compensated asynchronously. Completed orders require the organizer refund endpoint."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Cancelled checkout",
        content = @Content(schema = @Schema(implementation = CheckoutView.class))
    )
    public ResponseEntity<CheckoutView> cancel(@PathVariable UUID id) {
        return response(checkout.cancel(id, actor.requireUserId()));
    }

    @PostMapping("/{id}/refund")
    @Operation(
        summary = "Request a full organizer refund",
        description = "Only unused tickets are eligible. Free orders are cancelled locally. Admission is blocked while a paid refund is pending; poll checkout status for completion."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Current refund or cancellation state",
        content = @Content(schema = @Schema(implementation = CheckoutView.class))
    )
    public ResponseEntity<CheckoutView> refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return response(checkout.refund(id, actor.requireUserId(), request.reason()));
    }

    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
