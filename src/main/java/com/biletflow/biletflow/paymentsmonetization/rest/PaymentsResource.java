package com.biletflow.biletflow.paymentsmonetization.rest;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.paymentsmonetization.application.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentsOhs;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentsResource {

    private final PaymentService service;
    private final PaymentAccessService access;
    private final PaymentSessionService sessions;
    private final ActivationRepository activations;
    private final PaymentLedger ledger;
    private final CurrentActor actor;
    private final Clock clock;

    public PaymentsResource(
        PaymentService service,
        PaymentAccessService access,
        PaymentSessionService sessions,
        ActivationRepository activations,
        PaymentLedger ledger,
        CurrentActor actor,
        Clock clock
    ) {
        this.service = service;
        this.access = access;
        this.sessions = sessions;
        this.activations = activations;
        this.ledger = ledger;
        this.actor = actor;
        this.clock = clock;
    }

    @GetMapping("/operations")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<java.util.List<PaymentsOhs.PaymentView>> operations() {
        return response(service.requiringReview());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentsOhs.PaymentView> get(@PathVariable UUID id) {
        service.authorized(id, actor.requireUserId());
        return response(service.get(id));
    }

    @PostMapping("/{id}/session")
    @Operation(
        summary = "Get the Halyk sandbox payment form for halyk.pay(paymentObject)",
        description = "Use scriptUrl and paymentObject. Merchant secrets stay on the server. Browser redirects never confirm a purchase; poll checkout status."
    )
    public ResponseEntity<PaymentGateway.Session> session(@PathVariable UUID id) {
        long user = actor.requireUserId();
        var w = service.authorized(id, user);
        if (w.payerId != user) throw new org.springframework.security.access.AccessDeniedException(
            "Only the payer can open this payment session"
        );
        if (w.abandoned || !"PENDING".equals(w.status()) || !clock.instant().isBefore(w.deadline)) throw new IllegalStateException(
            "Payment session is no longer available"
        );
        // The persisted form is reused on retries; creating a second session cannot trigger a second payment.
        return response(sessions.open(w));
    }

    public record ActivationRequest(boolean acceptSandboxTerms, boolean simulateIdentityAndPayoutVerification) {}

    @PostMapping("/events/{eventId}/activation")
    public ResponseEntity<Map<String, Object>> activate(@PathVariable UUID eventId, @RequestBody ActivationRequest request) {
        UUID payment = service.activate(
            eventId,
            actor.requireUserId(),
            request.acceptSandboxTerms(),
            request.simulateIdentityAndPayoutVerification()
        );
        return response(Map.of("paymentId", payment, "sandbox", true, "verificationSimulated", true));
    }

    @GetMapping("/events/{eventId}/activation")
    public ResponseEntity<Map<String, Object>> activation(@PathVariable UUID eventId) {
        var a = activations
            .find(eventId)
            .orElseThrow(() -> new com.biletflow.biletflow.common.application.EntityNotFoundException("Activation not found"));
        service.authorized(a.paymentId(), actor.requireUserId());
        return response(
            Map.of(
                "eventId",
                eventId,
                "status",
                a.aggregate().getStatus().name(),
                "paymentId",
                a.paymentId(),
                "sandbox",
                true,
                "verificationSimulated",
                a.verificationSimulated()
            )
        );
    }

    @GetMapping("/revenue/mine")
    public ResponseEntity<Map<String, Object>> revenue() {
        return response(Map.of("sandbox", true, "feesEstimated", true, "currency", "KZT", "totals", ledger.summary(actor.requireUserId())));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Callback(String invoiceId, @JsonProperty("secret_hash") String secret) {}

    @PostMapping("/halyk/callback")
    public ResponseEntity<Void> callback(@RequestBody Callback body) {
        access.callback(body.invoiceId(), body.secret());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
    }

    public record Resolution(UUID expectedOperationId, String resolution, String bankSupportReference) {}

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/{id}/resolve-operation")
    @Operation(
        summary = "Resolve an uncertain sandbox bank operation using independently verified bank evidence",
        description = "Do not retry solely because the bank still reports CHARGE or AUTH. Confirm with the bank that the operation was not executed. The action is audited."
    )
    public ResponseEntity<Void> resolve(@PathVariable UUID id, @RequestBody Resolution resolution) {
        access.resolve(
            id,
            actor.requireUserId(),
            resolution.expectedOperationId(),
            resolution.resolution(),
            resolution.bankSupportReference()
        );
        return ResponseEntity.noContent().build();
    }

    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
