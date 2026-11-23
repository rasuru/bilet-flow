package com.biletflow.biletflow.ordercheckout.rest;

import com.biletflow.biletflow.ordercheckout.application.refundpolicy.RefundPolicyService;
import com.biletflow.biletflow.ordercheckout.application.refundpolicy.RefundPolicyView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/refund-policy")
public class RefundPolicyResource {

    private final RefundPolicyService policies;

    public RefundPolicyResource(RefundPolicyService policies) {
        this.policies = Objects.requireNonNull(policies);
    }

    public record UpdateRequest(@NotNull Boolean refundsAllowed, @Min(0) @Max(720) int cutoffHoursBeforeEvent) {}

    @GetMapping
    public RefundPolicyView get(@PathVariable UUID eventId) {
        return policies.get(eventId);
    }

    @PutMapping
    public RefundPolicyView update(@PathVariable UUID eventId, @Valid @RequestBody UpdateRequest request) {
        return policies.update(eventId, request.refundsAllowed(), request.cutoffHoursBeforeEvent());
    }
}
