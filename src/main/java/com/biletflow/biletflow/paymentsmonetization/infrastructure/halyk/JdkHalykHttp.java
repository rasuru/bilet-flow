package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

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
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new HalykGateway.GatewayUnavailable();
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HalykGateway.GatewayUnavailable();
        } catch (java.io.IOException e) {
            throw new HalykGateway.GatewayUnavailable();
        }
    }
}
