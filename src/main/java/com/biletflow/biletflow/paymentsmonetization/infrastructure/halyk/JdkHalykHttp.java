package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentProviderUnavailableException;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class JdkHalykHttp implements HalykHttp {

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Override
    public String send(HttpRequest request) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new PaymentProviderUnavailableException();
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderUnavailableException();
        } catch (java.io.IOException e) {
            throw new PaymentProviderUnavailableException();
        }
    }
}
