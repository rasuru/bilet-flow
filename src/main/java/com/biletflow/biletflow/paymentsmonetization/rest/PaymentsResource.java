package com.biletflow.biletflow.paymentsmonetization.rest;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.paymentsmonetization.application.*;
import com.biletflow.biletflow.paymentsmonetization.application.command.*;
import com.biletflow.biletflow.paymentsmonetization.application.view.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.OperationResolution;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.PaymentView;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Sandbox payments, event activation, revenue and reconciliation operations.")
@ApiResponses({
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "401",
        description = "Authentication required for private payment operations",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "403",
        description = "Payment access denied or callback authentication failed",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Payment or activation not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Payment state conflict",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "503",
        description = "Payment provider temporarily unavailable",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
})
public class PaymentsResource {

    private final PaymentService service;
    private final PaymentAccessService access;
    private final PaymentSessionService sessions;
    private final PaymentQueryService queries;
    private final CurrentActor actor;

    public PaymentsResource(
        PaymentService service,
        PaymentAccessService access,
        PaymentSessionService sessions,
        PaymentQueryService queries,
        CurrentActor actor
    ) {
        this.service = Objects.requireNonNull(service);
        this.access = Objects.requireNonNull(access);
        this.sessions = Objects.requireNonNull(sessions);
        this.queries = Objects.requireNonNull(queries);
        this.actor = Objects.requireNonNull(actor);
    }

    @GetMapping("/operations")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
        summary = "List payments requiring operator review",
        description = "Includes provider data conflicts and bank operations unresolved after five minutes. Administrator access is required."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Review queue, including an empty list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentView.class)))
    )
    public ResponseEntity<List<PaymentView>> operations() {
        return response(service.requiringReview());
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get verified payment and refund status",
        description = "Available to the payer and authorized event organizers. Browser redirects do not confirm payment."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Current payment",
        content = @Content(schema = @Schema(implementation = PaymentView.class))
    )
    public ResponseEntity<PaymentView> get(@PathVariable UUID id) {
        service.authorized(id, actor.requireUserId());
        return response(service.get(id));
    }

    @PostMapping("/{id}/session")
    @Operation(
        summary = "Get the Halyk sandbox form",
        description = "Only the payer may open a pending unexpired payment. Load scriptUrl and call halyk.pay(paymentObject). The persisted form is reused after retries; merchant credentials stay on the server."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Reusable sandbox form",
        content = @Content(schema = @Schema(implementation = PaymentGateway.Session.class))
    )
    public ResponseEntity<PaymentGateway.Session> session(@PathVariable UUID id) {
        return response(sessions.open(service.authorizedPayer(id, actor.requireUserId())));
    }

    public record ActivationRequest(@AssertTrue boolean acceptSandboxTerms, @AssertTrue boolean simulateIdentityAndPayoutVerification) {}

    @PostMapping("/events/{eventId}/activation")
    @Operation(
        summary = "Request paid sales activation",
        description = "Organizer-only. Terms and simulated verification must be accepted. The returned activation fee payment must settle before paid sales become active."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Current or new activation fee payment",
        content = @Content(schema = @Schema(implementation = ActivationPaymentView.class))
    )
    public ResponseEntity<ActivationPaymentView> activate(@PathVariable UUID eventId, @Valid @RequestBody ActivationRequest request) {
        UUID payment = service.activate(
            new ActivatePaidSalesCommand(
                eventId,
                actor.requireUserId(),
                request.acceptSandboxTerms(),
                request.simulateIdentityAndPayoutVerification()
            )
        );
        return response(new ActivationPaymentView(payment, true, true));
    }

    @GetMapping("/events/{eventId}/activation")
    @Operation(
        summary = "Get event paid sales activation",
        description = "Available to the activation payer and authorized event organizers."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Current activation state",
        content = @Content(schema = @Schema(implementation = ActivationView.class))
    )
    public ResponseEntity<ActivationView> activation(@PathVariable UUID eventId) {
        return response(queries.activation(eventId, actor.requireUserId()));
    }

    @GetMapping("/revenue/mine")
    @Operation(
        summary = "Get the current organizer's estimated revenue",
        description = "Summarizes immutable charges, refunds and estimated fees in KZT. This is not a bank payout balance."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Revenue summary",
        content = @Content(schema = @Schema(implementation = RevenueView.class))
    )
    public ResponseEntity<RevenueView> revenue() {
        return response(queries.revenue(actor.requireUserId()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Callback(String invoiceId, @JsonProperty("secret_hash") String secret) {}

    @PostMapping("/halyk/callback")
    @Operation(
        summary = "Receive an authenticated provider reconciliation hint",
        description = "Uses the payment callback secret instead of a user JWT. Callback fields never confirm payment; the server independently queries provider status."
    )
    @ApiResponse(responseCode = "200", description = "Reconciliation scheduled")
    public ResponseEntity<Void> callback(@RequestBody Callback request) {
        access.callback(request.invoiceId(), request.secret());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
    }

    public record Resolution(
        UUID expectedOperationId,
        @NotNull OperationResolution resolution,
        @NotBlank @Size(max = 200) String bankSupportReference
    ) {}

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/{id}/resolve-operation")
    @Operation(
        summary = "Resolve an uncertain bank operation",
        description = "Requires independently verified bank evidence and the current operation ID. A pending provider status alone is not sufficient evidence for retry. The actor and evidence reference are audited."
    )
    @ApiResponse(responseCode = "204", description = "Operator resolution recorded")
    public ResponseEntity<Void> resolve(@PathVariable UUID id, @Valid @RequestBody Resolution request) {
        access.resolve(
            new ResolvePaymentOperationCommand(
                id,
                actor.requireUserId(),
                request.expectedOperationId(),
                request.resolution(),
                request.bankSupportReference()
            )
        );
        return ResponseEntity.noContent().build();
    }

    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
