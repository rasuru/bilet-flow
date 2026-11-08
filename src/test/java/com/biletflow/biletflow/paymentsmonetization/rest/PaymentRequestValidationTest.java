package com.biletflow.biletflow.paymentsmonetization.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.ordercheckout.rest.CheckoutResource.CheckoutRequest;
import com.biletflow.biletflow.ordercheckout.rest.CheckoutResource.SelectionRequest;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

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

    @Test
    void suspensionRequiresAReasonAndTheFeeMustBeValid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new PaymentAdministrationResource.ReasonRequest(" ")).isEmpty());
            assertFalse(validator.validate(new PaymentAdministrationResource.ReasonRequest("x".repeat(201))).isEmpty());
            assertTrue(validator.validate(new PaymentAdministrationResource.ReasonRequest("Fraud investigation")).isEmpty());
            assertFalse(validator.validate(new PaymentAdministrationResource.ActivationFeeRequest(null)).isEmpty());
            assertFalse(validator.validate(new PaymentAdministrationResource.ActivationFeeRequest(BigDecimal.ZERO)).isEmpty());
            assertFalse(validator.validate(new PaymentAdministrationResource.ActivationFeeRequest(new BigDecimal("10.001"))).isEmpty());
            assertTrue(validator.validate(new PaymentAdministrationResource.ActivationFeeRequest(new BigDecimal("1500.00"))).isEmpty());
        }
    }

    @Test
    void payoutAccountNeedsHolderIbanAndSimulationConsent() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new PayoutAccountResource.Request(" ", "KZ86125KZT5004100100", true)).isEmpty());
            assertFalse(validator.validate(new PayoutAccountResource.Request("Holder", "", true)).isEmpty());
            assertFalse(validator.validate(new PayoutAccountResource.Request("Holder", "KZ86125KZT5004100100", false)).isEmpty());
            assertFalse(validator.validate(new PayoutAccountResource.Request("x".repeat(101), "KZ86125KZT5004100100", true)).isEmpty());
            assertTrue(validator.validate(new PayoutAccountResource.Request("Holder", "KZ86 125K ZT50 0410 0100", true)).isEmpty());
        }
    }

    @Test
    void processingFeeRateMustBeAPercentageBelowOneHundred() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new PaymentAdministrationResource.ProcessingFeeRateRequest(null)).isEmpty());
            assertFalse(validator.validate(new PaymentAdministrationResource.ProcessingFeeRateRequest(new BigDecimal("-0.01"))).isEmpty());
            assertFalse(validator.validate(new PaymentAdministrationResource.ProcessingFeeRateRequest(new BigDecimal("100"))).isEmpty());
            assertFalse(validator.validate(new PaymentAdministrationResource.ProcessingFeeRateRequest(new BigDecimal("2.555"))).isEmpty());
            assertTrue(validator.validate(new PaymentAdministrationResource.ProcessingFeeRateRequest(BigDecimal.ZERO)).isEmpty());
            assertTrue(validator.validate(new PaymentAdministrationResource.ProcessingFeeRateRequest(new BigDecimal("2.5"))).isEmpty());
        }
    }
}
