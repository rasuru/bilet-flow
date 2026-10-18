package com.biletflow.biletflow.paymentsmonetization.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Sandbox only. Production endpoints and real-money configuration are intentionally unsupported. */
@Component
public class HalykProperties {

    public final String clientId;
    public final String clientSecret;
    public final String terminalId;
    public final String publicBaseUrl;
    public final String returnUrl;

    public HalykProperties(
        @Value("${biletflow.payments.halyk.client-id:test}") String clientId,
        @Value("${biletflow.payments.halyk.client-secret:}") String clientSecret,
        @Value("${biletflow.payments.halyk.terminal-id:67e34d63-102f-4bd1-898e-370781d0074d}") String terminalId,
        @Value("${biletflow.payments.public-base-url:http://localhost:8080}") String publicBaseUrl,
        @Value("${biletflow.payments.return-url:http://localhost:9000/orders}") String returnUrl
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.terminalId = terminalId;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.returnUrl = returnUrl;
    }
}
