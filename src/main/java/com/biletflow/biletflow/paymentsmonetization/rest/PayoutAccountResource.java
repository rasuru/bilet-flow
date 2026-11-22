package com.biletflow.biletflow.paymentsmonetization.rest;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.paymentsmonetization.application.PayoutAccountService;
import com.biletflow.biletflow.paymentsmonetization.application.command.RegisterPayoutAccountCommand;
import com.biletflow.biletflow.paymentsmonetization.application.view.PayoutAccountView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Objects;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/payout-account")
@Tag(
    name = "Organizer payout account",
    description = "Simulated payout account (SRS 3.2). The IBAN is format-checked only; no bank verifies it and only the last four characters are stored."
)
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid IBAN, holder name or missing simulation consent"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
})
public class PayoutAccountResource {

    private final PayoutAccountService accounts;
    private final CurrentActor actor;

    public PayoutAccountResource(PayoutAccountService accounts, CurrentActor actor) {
        this.accounts = Objects.requireNonNull(accounts);
        this.actor = Objects.requireNonNull(actor);
    }

    public record Request(
        @NotBlank @Size(max = 100) String holderName,
        @NotBlank @Size(max = 40) String iban,
        @NotNull @AssertTrue Boolean acceptSimulation
    ) {}

    @GetMapping
    @Operation(summary = "Get the current organizer's payout account", description = "The IBAN is returned masked.")
    @ApiResponse(
        responseCode = "200",
        description = "Registered payout account",
        content = @Content(schema = @Schema(implementation = PayoutAccountView.class))
    )
    @ApiResponse(responseCode = "404", description = "No payout account registered yet")
    public ResponseEntity<PayoutAccountView> get() {
        return response(accounts.get(actor.requireUserId()));
    }

    @PutMapping
    @Operation(
        summary = "Register or replace the current organizer's payout account",
        description = "Required before activating paid sales and before requesting a payout. Accepts a Kazakhstan IBAN with or without spaces. Replacing the account does not change payouts already requested. The change is audited."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Registered payout account",
        content = @Content(schema = @Schema(implementation = PayoutAccountView.class))
    )
    public ResponseEntity<PayoutAccountView> register(@Valid @RequestBody Request request) {
        return response(
            accounts.register(
                new RegisterPayoutAccountCommand(
                    actor.requireUserId(),
                    request.holderName(),
                    request.iban(),
                    Boolean.TRUE.equals(request.acceptSimulation())
                )
            )
        );
    }

    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
