package com.biletflow.biletflow.paymentsmonetization.rest;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.paymentsmonetization.application.PayoutService;
import com.biletflow.biletflow.paymentsmonetization.application.command.RequestPayoutCommand;
import com.biletflow.biletflow.paymentsmonetization.application.view.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/payouts")
@Tag(name = "Organizer payouts", description = "Internal KZT payout simulation. No Halyk payout API or real bank transfer is used.")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid amount, request ID, pagination or simulation consent"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Payout belongs to another organizer"),
    @ApiResponse(responseCode = "404", description = "Payout not found"),
    @ApiResponse(
        responseCode = "409",
        description = "Insufficient funds, unresolved payment/refund, or request ID reused with a different amount"
    ),
})
public class PayoutResource {

    private final PayoutService payouts;
    private final CurrentActor actor;

    public PayoutResource(PayoutService payouts, CurrentActor actor) {
        this.payouts = Objects.requireNonNull(payouts);
        this.actor = Objects.requireNonNull(actor);
    }

    public record Request(
        @NotNull UUID requestId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amountKzt,
        @NotNull @AssertTrue Boolean acceptSimulation
    ) {}

    @GetMapping("/balance")
    @Operation(
        summary = "Get the current organizer's simulated payout balance",
        description = "Shows available, withheld, reserved, paid-out and deficit amounts. Pending refunds or reviews block payout requests."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Current simulated KZT balance",
        content = @Content(schema = @Schema(implementation = PayoutBalanceView.class))
    )
    public ResponseEntity<PayoutBalanceView> balance() {
        return response(payouts.balance(actor.requireUserId()));
    }

    @GetMapping
    @Operation(
        summary = "List the current organizer's simulated payouts",
        description = "Newest first. Offset defaults to zero; limit defaults to 50 and cannot exceed 100."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Payout history, possibly empty",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PayoutView.class)))
    )
    public ResponseEntity<List<PayoutView>> list(
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return response(payouts.list(actor.requireUserId(), offset, limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a simulated payout", description = "Only the organizer who requested it can read it.")
    @ApiResponse(
        responseCode = "200",
        description = "Current payout state",
        content = @Content(schema = @Schema(implementation = PayoutView.class))
    )
    public ResponseEntity<PayoutView> get(@PathVariable UUID id) {
        return response(payouts.get(id, actor.requireUserId()));
    }

    @PostMapping
    @Operation(
        summary = "Request a simulated organizer payout",
        description = "The client supplies a UUID requestId used as payout identity. Retry with the same ID and amount to reuse the request, including after failure. A new ID creates a new request. The amount is reserved atomically; completion is asynchronous."
    )
    @ApiResponse(
        responseCode = "200",
        description = "New or existing simulated payout",
        content = @Content(schema = @Schema(implementation = PayoutView.class))
    )
    public ResponseEntity<PayoutView> request(@Valid @RequestBody Request request) {
        return response(
            payouts.request(
                new RequestPayoutCommand(
                    actor.requireUserId(),
                    request.requestId(),
                    request.amountKzt(),
                    Boolean.TRUE.equals(request.acceptSimulation())
                )
            )
        );
    }

    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
