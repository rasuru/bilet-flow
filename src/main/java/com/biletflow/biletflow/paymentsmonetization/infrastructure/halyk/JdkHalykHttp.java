package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentProviderUnavailableException;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class JdkHalykHttp implements HalykHttp {

    private final HttpClient http;

    public JdkHalykHttp() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    JdkHalykHttp(HttpClient http) {
        this.http = java.util.Objects.requireNonNull(http);
    }

    private PaymentProviderUnavailableException.Phase phase(HttpRequest request) {
        String path = request.uri().getPath();
        if (path.equals("/oauth2/token")) return PaymentProviderUnavailableException.Phase.TOKEN;
        if (path.startsWith("/check-status/")) return PaymentProviderUnavailableException.Phase.STATUS;
        if (path.startsWith("/operation/")) return PaymentProviderUnavailableException.Phase.MUTATION;
        return PaymentProviderUnavailableException.Phase.UNKNOWN;
    }

    @Override
    public String send(HttpRequest request) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new PaymentProviderUnavailableException(
                PaymentProviderUnavailableException.Kind.HTTP,
                response.statusCode(),
                phase(request)
            );
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderUnavailableException(PaymentProviderUnavailableException.Kind.INTERRUPTED, null, phase(request));
        } catch (HttpTimeoutException e) {
            throw new PaymentProviderUnavailableException(PaymentProviderUnavailableException.Kind.TIMEOUT, null, phase(request));
        } catch (java.io.IOException e) {
            throw new PaymentProviderUnavailableException(PaymentProviderUnavailableException.Kind.CONNECTION, null, phase(request));
        }
    }
}
