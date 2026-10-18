package com.biletflow.biletflow.ordercheckout.rest;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ordercheckout.application.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkouts")
public class CheckoutResource {

    private final CheckoutService checkout;
    private final CurrentActor actor;

    public CheckoutResource(CheckoutService checkout, CurrentActor actor) {
        this.checkout = checkout;
        this.actor = actor;
    }

    @PostMapping
    @Operation(
        summary = "Reserve server-priced tickets and create a free or sandbox paid checkout",
        description = "Idempotency-Key is a new UUID per checkout. Reuse it with the exact same request after a timeout. No client prices are accepted."
    )
    public ResponseEntity<CheckoutService.View> create(
        @RequestHeader("Idempotency-Key") UUID key,
        @RequestBody CheckoutService.Request body
    ) {
        return response(checkout.create(key, actor.requireUserId(), body));
    }

    @GetMapping
    public ResponseEntity<List<CheckoutService.View>> mine() {
        return response(checkout.mine(actor.requireUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CheckoutService.View> get(@PathVariable UUID id) {
        return response(checkout.get(id, actor.requireUserId()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<CheckoutService.View> cancel(@PathVariable UUID id) {
        return response(checkout.cancel(id, actor.requireUserId()));
    }

    public record RefundRequest(String reason) {}

    @PostMapping("/{id}/refund")
    @Operation(
        summary = "Organizer requests a full refund, or cancels a free registration",
        description = "Only unused tickets are eligible. Paid tickets cannot be admitted while the refund is pending. Poll GET for its asynchronous outcome."
    )
    public ResponseEntity<CheckoutService.View> refund(@PathVariable UUID id, @RequestBody RefundRequest request) {
        return response(checkout.refund(id, actor.requireUserId(), request.reason()));
    }

    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
