package com.biletflow.biletflow.paymentsmonetization.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class PaidSalesActivationBypass {

    private final boolean enabled;

    public PaidSalesActivationBypass(
        Environment environment,
        @Value("${BILETFLOW_DEV_BYPASS_PAID_SALES_ACTIVATION:false}") boolean requested
    ) {
        enabled = requested && environment.acceptsProfiles(Profiles.of("dev & !prod"));
    }

    public boolean enabled() {
        return enabled;
    }
}
