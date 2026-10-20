package com.biletflow.biletflow.paymentsmonetization.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.ordercheckout.rest.CheckoutResource.CheckoutRequest;
import com.biletflow.biletflow.ordercheckout.rest.CheckoutResource.SelectionRequest;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentRequestValidationTest {

    @Test
    void invalidNestedCheckoutInputIsRejectedBeforeApplicationCalls() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new CheckoutRequest(null, List.of())).isEmpty());
            assertFalse(
                validator
                    .validate(
                        new CheckoutRequest(
                            UUID.randomUUID(),
                            List.of(new SelectionRequest(UUID.randomUUID(), List.of(), List.of("invalid-email")))
                        )
                    )
                    .isEmpty()
            );
            assertTrue(
                validator
                    .validate(
                        new CheckoutRequest(
                            UUID.randomUUID(),
                            List.of(new SelectionRequest(UUID.randomUUID(), List.of(), List.of("attendee@example.com")))
                        )
                    )
                    .isEmpty()
            );
        }
    }

    @Test
    void operatorEvidenceAndActivationConsentAreRequired() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new PaymentsResource.Resolution(null, null, " ")).isEmpty());
            assertFalse(validator.validate(new PaymentsResource.ActivationRequest(false, true)).isEmpty());
            assertTrue(validator.validate(new PaymentsResource.ActivationRequest(true, true)).isEmpty());
        }
    }
}
