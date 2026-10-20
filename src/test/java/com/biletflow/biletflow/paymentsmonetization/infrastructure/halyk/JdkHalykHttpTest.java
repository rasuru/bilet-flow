package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentProviderUnavailableException;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;

class JdkHalykHttpTest {

    private final HttpClient client = mock(HttpClient.class);
    private final HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.com/operation/refund"))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    private final JdkHalykHttp transport = new JdkHalykHttp(client);

    @Test
    void preservesHttpStatusWithoutLeakingBankResponseOrCredentials() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("merchant-secret scoped-token private-card-data");
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(
            response
        );
        var error = assertThrows(PaymentProviderUnavailableException.class, () -> transport.send(request));
        assertEquals("MUTATION HTTP 403", error.diagnostic());
        assertFalse(error.toString().contains("merchant-secret"));
        assertFalse(error.toString().contains("scoped-token"));
        assertNull(error.getCause());
        var tokenRequest = HttpRequest.newBuilder(URI.create("https://example.com/oauth2/token"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        var tokenFailure = assertThrows(PaymentProviderUnavailableException.class, () -> transport.send(tokenRequest));
        assertEquals("TOKEN HTTP 403", tokenFailure.diagnostic());
    }

    @Test
    void distinguishesTimeoutFromHttpRejectionWithoutLeakingExceptionText() throws Exception {
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(
            new HttpTimeoutException("private request details")
        );
        var error = assertThrows(PaymentProviderUnavailableException.class, () -> transport.send(request));
        assertEquals("MUTATION TIMEOUT", error.diagnostic());
        assertFalse(error.toString().contains("private request details"));
    }

    @Test
    void reportsConnectionFailureWithoutTreatingItAsProofOfRejection() throws Exception {
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(
            new IOException("private request details")
        );
        var error = assertThrows(PaymentProviderUnavailableException.class, () -> transport.send(request));
        assertEquals("MUTATION CONNECTION", error.diagnostic());
        assertFalse(error.toString().contains("private request details"));
    }

    @Test
    void interruptionRetainsTheInterruptFlag() throws Exception {
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(
            new InterruptedException("private request details")
        );
        try {
            var error = assertThrows(PaymentProviderUnavailableException.class, () -> transport.send(request));
            assertEquals("MUTATION INTERRUPTED", error.diagnostic());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
