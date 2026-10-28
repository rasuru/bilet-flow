package com.biletflow.biletflow.paymentsmonetization.rest;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.paymentsmonetization.application.PaidSalesAdministrationService;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentAdminQueries;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentAdminQueryService;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentSettingsService;
import com.biletflow.biletflow.paymentsmonetization.application.command.SuspendPaidSalesCommand;
import com.biletflow.biletflow.paymentsmonetization.application.view.ActivationFeeView;
import com.biletflow.biletflow.paymentsmonetization.application.view.ActivationRecordView;
import com.biletflow.biletflow.paymentsmonetization.application.view.AdminPaymentView;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivationStatus;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentPurpose;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/admin")
@Tag(
    name = "Payments administration",
    description = "Platform-administrator payment search, paid-sales suspension and activation fee settings (SRS 4.5, 4.12)."
)
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
        description = "Administrator access required",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Activation not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
    @ApiResponse(
        responseCode = "409",
        description = "Paid-sales state does not allow the change",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    ),
})
public class PaymentAdministrationResource {

    private final PaymentAdminQueryService queries;
    private final PaidSalesAdministrationService paidSales;
    private final PaymentSettingsService settings;
    private final CurrentActor actor;

    public PaymentAdministrationResource(
        PaymentAdminQueryService queries,
        PaidSalesAdministrationService paidSales,
        PaymentSettingsService settings,
        CurrentActor actor
    ) {
        this.queries = Objects.requireNonNull(queries);
        this.paidSales = Objects.requireNonNull(paidSales);
        this.settings = Objects.requireNonNull(settings);
        this.actor = Objects.requireNonNull(actor);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
        summary = "Search ticket and activation-fee payments",
        description = "All filters are optional. Use status=FAILED to monitor payment failures. Newest payment deadline first; limit defaults to 50 and cannot exceed 100."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Matching payments, including an empty list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = AdminPaymentView.class)))
    )
    public ResponseEntity<List<AdminPaymentView>> payments(
        @RequestParam(required = false) PaymentStatus status,
        @RequestParam(required = false) PaymentPurpose purpose,
        @RequestParam(required = false) UUID eventId,
        @RequestParam(required = false) Long payerId,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return response(queries.payments(new PaymentAdminQueries.PaymentFilter(status, purpose, eventId, payerId), offset, limit));
    }

    @GetMapping("/activations")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
        summary = "List paid-sales activation records",
        description = "Optionally filtered by status. Newest first; limit defaults to 50 and cannot exceed 100."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Activation records, including an empty list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ActivationRecordView.class)))
    )
    public ResponseEntity<List<ActivationRecordView>> activations(
        @RequestParam(required = false) EventPaidSalesActivationStatus status,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return response(queries.activations(status, offset, limit));
    }

    public record SuspendRequest(@NotBlank @Size(max = 200) String reason) {}

    @PostMapping("/events/{eventId}/paid-sales/suspend")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
        summary = "Suspend an event's paid sales",
        description = "SRS 4.5. New paid checkouts are rejected immediately; payments already started still settle and issued tickets stay valid. The fee is kept and the reason is audited. Requesting activation again cannot lift the suspension."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Suspended activation",
        content = @Content(schema = @Schema(implementation = ActivationRecordView.class))
    )
    public ResponseEntity<ActivationRecordView> suspend(@PathVariable UUID eventId, @Valid @RequestBody SuspendRequest request) {
        return response(paidSales.suspend(new SuspendPaidSalesCommand(eventId, actor.requireUserId(), request.reason())));
    }

    @GetMapping("/settings/activation-fee")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Get the activation fee for new requests")
    @ApiResponse(
        responseCode = "200",
        description = "Current fee",
        content = @Content(schema = @Schema(implementation = ActivationFeeView.class))
    )
    public ResponseEntity<ActivationFeeView> activationFee() {
        return response(settings.activationFee());
    }

    public record ActivationFeeRequest(@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amountKzt) {}

    @PutMapping("/settings/activation-fee")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
        summary = "Change the activation fee for new requests",
        description = "Takes effect without a restart. Unpaid fee attempts keep their original amount and paid fees are unaffected. The change is audited."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Updated fee",
        content = @Content(schema = @Schema(implementation = ActivationFeeView.class))
    )
    public ResponseEntity<ActivationFeeView> changeActivationFee(@Valid @RequestBody ActivationFeeRequest request) {
        return response(settings.changeActivationFee(request.amountKzt(), actor.requireUserId()));
    }

    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
