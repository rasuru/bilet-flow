package com.biletflow.biletflow.paymentsmonetization.rest;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.paymentsmonetization.application.MockCheckoutService;
import com.biletflow.biletflow.paymentsmonetization.application.MockPaymentScenarios.Scenario;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentReconciler;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/{id}/mock")
public class MockCheckoutResource {

    private final CurrentActor actor;
    private final MockCheckoutService checkout;
    private final PaymentReconciler reconciler;

    public MockCheckoutResource(CurrentActor actor, MockCheckoutService checkout, PaymentReconciler reconciler) {
        this.actor = actor;
        this.checkout = checkout;
        this.reconciler = reconciler;
    }

    @GetMapping
    @Operation(summary = "Read the payer's simulated checkout")
    public ResponseEntity<MockCheckoutService.CheckoutView> get(@PathVariable UUID id) {
        return response(checkout.get(id, actor.requireUserId()));
    }

    public record OutcomeRequest(@NotNull Scenario scenario) {}

    @PostMapping
    @Operation(summary = "Choose an outcome for a mock payment")
    public ResponseEntity<MockCheckoutService.CheckoutView> choose(@PathVariable UUID id, @Valid @RequestBody OutcomeRequest request) {
        long payer = actor.requireUserId();
        checkout.choose(id, payer, request.scenario());
        // The scenario commits first. A leased or interrupted reconciliation is retried by the worker.
        reconciler.reconcile(id);
        return response(checkout.get(id, payer));
    }

    private ResponseEntity<MockCheckoutService.CheckoutView> response(MockCheckoutService.CheckoutView body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
