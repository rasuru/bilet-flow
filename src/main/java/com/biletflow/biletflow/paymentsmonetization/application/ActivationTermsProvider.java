package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentTerms;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ActivationTermsProvider {

    private final PaymentTerms configured;
    private final PaymentSettingsRepository settings;

    public ActivationTermsProvider(PaymentTerms configured, PaymentSettingsRepository settings) {
        this.configured = Objects.requireNonNull(configured, "configured cannot be null");
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
    }

    public PaymentTerms current() {
        return settings
            .activationFee()
            .map(s -> configured.withActivationFee(s.amountKzt()))
            .orElse(configured);
    }
}
