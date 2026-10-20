package com.biletflow.biletflow.paymentsmonetization.config;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Sandbox only. Production endpoints and real-money configuration are intentionally unsupported. */
@Component
public class HalykProperties {

    private final String clientId;
    private final String clientSecret;
    private final String terminalId;
    private final String publicBaseUrl;
    private final String returnUrl;

    public HalykProperties(
        @Value("${biletflow.payments.halyk.client-id:test}") String clientId,
        @Value("${biletflow.payments.halyk.client-secret:}") String clientSecret,
        @Value("${biletflow.payments.halyk.terminal-id:67e34d63-102f-4bd1-898e-370781d0074d}") String terminalId,
        @Value("${biletflow.payments.public-base-url:http://localhost:8080}") String publicBaseUrl,
        @Value("${biletflow.payments.return-url:http://localhost:9000/orders}") String returnUrl
    ) {
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret cannot be null");
        this.terminalId = Objects.requireNonNull(terminalId, "terminalId cannot be null");
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.returnUrl = Objects.requireNonNull(returnUrl, "returnUrl cannot be null");
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }
}
