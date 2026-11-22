package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentTerms;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PaymentTermsProvider {

    private final PaymentTerms configured;
    private final PaymentSettingsRepository settings;

    public PaymentTermsProvider(PaymentTerms configured, PaymentSettingsRepository settings) {
        this.configured = Objects.requireNonNull(configured, "configured cannot be null");
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
    }

    public PaymentTerms current() {
        var terms = settings
            .activationFee()
            .map(s -> configured.withActivationFee(s.amountKzt()))
            .orElse(configured);
        var rate = settings.processingFeeRate();
        return rate.isPresent() ? terms.withProcessingFeeRate(rate.orElseThrow().rate()) : terms;
    }
}
