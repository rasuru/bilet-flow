package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.config.HalykProperties;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.SettlementStatus;
import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HalykGatewayTest {

    private static final String TOKEN = "{\"access_token\":\"scoped-token\",\"expires_in\":7200,\"token_type\":\"Bearer\"}";
    private final HalykProperties config = new HalykProperties(
        "test",
        "merchant-secret",
        "67e34d63-102f-4bd1-898e-370781d0074d",
        "https://example.com",
        "https://example.com/return",
        new BigDecimal("1000"),
        new BigDecimal("0.03")
    );
    private final Deque<String> responses = new ArrayDeque<>();
    private final List<HttpRequest> requests = new ArrayList<>();

    private HalykGateway gateway() {
        return new HalykGateway(config, JsonMapper.builder().build(), request -> {
            requests.add(request);
            return responses.removeFirst();
        });
    }

    @Test
    void paymentTokenIsBoundAndBrowserObjectContainsNoMerchantSecret() throws Exception {
        responses.add(TOKEN);
        var work = new PaymentWork();
        work.id = UUID.randomUUID();
        work.invoiceId = "123456";
        work.amount = new BigDecimal("123.45");
        work.callbackSecret = "bank-only-secret";
        var session = gateway().session(work);
        String body = body(requests.get(0));
        assertTrue(requests.get(0).uri().toString().equals("https://test-epay-oauth.epayment.kz/oauth2/token"));
        for (String field : List.of("invoiceID", "amount", "currency", "terminal", "secret_hash", "postLink", "failurePostLink"))
            assertTrue(body.contains("name=\"" + field + "\""));
        assertTrue(body.contains("123456") && body.contains("123.45") && body.contains("bank-only-secret"));
        assertEquals("https://test-epay.epayment.kz/payform/payment-api.js", session.scriptUrl());
        assertFalse(session.paymentObject().toString().contains("merchant-secret"));
        assertFalse(session.paymentObject().toString().contains("bank-only-secret"));
        assertTrue(session.paymentObject().get("data").toString().contains(work.id.toString()));
    }

    @Test
    void resultCode100IsNotNecessarilyCaptured() {
        UUID payment = UUID.randomUUID();
        responses.add(TOKEN);
        responses.add(transaction("AUTH", payment));
        var observation = gateway().inspect("123456");
        assertEquals(SettlementStatus.AUTHORIZED, observation.status());
        assertEquals(new BigDecimal("123.45"), observation.amount());
        assertEquals(payment.toString(), observation.paymentId());
        assertTrue(requests.get(1).uri().toString().endsWith("/check-status/payment/transaction/123456"));
    }

    @Test
    void notFoundAndTransientErrorsCannotBecomePaymentFailure() {
        responses.add(TOKEN);
        responses.add("{\"resultCode\":\"102\",\"transaction\":null}");
        assertEquals(SettlementStatus.NOT_FOUND, gateway().inspect("123456").status());
        responses.add(TOKEN);
        responses.add("{\"resultCode\":\"103\",\"transaction\":null}");
        assertThrows(HalykGateway.GatewayUnavailable.class, () -> gateway().inspect("123456"));
    }

    @Test
    void fullRefundUsesTransactionIdAndStableExternalId() {
        responses.add(TOKEN);
        responses.add("");
        UUID transaction = UUID.randomUUID(),
            refund = UUID.randomUUID();
        gateway().mutate(transaction.toString(), "refund", refund.toString());
        var request = requests.get(1);
        assertEquals("POST", request.method());
        assertEquals(
            "https://test-epay-api.epayment.kz/operation/" + transaction + "/refund?externalID=" + refund,
            request.uri().toString()
        );
        assertEquals("Bearer scoped-token", request.headers().firstValue("Authorization").orElseThrow());
    }

    private String transaction(String status, UUID payment) {
        return (
            "{\"resultCode\":\"100\",\"transaction\":{\"id\":\"00000000-0000-0000-0000-000000000001\",\"invoiceID\":\"123456\",\"amount\":123.45,\"currency\":\"KZT\",\"terminalID\":\"" +
            config.terminalId +
            "\",\"statusName\":\"" +
            status +
            "\",\"data\":\"{\\\"biletflowPaymentId\\\":\\\"" +
            payment +
            "\\\"}\"}}"
        );
    }

    private String body(HttpRequest request) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        var done = new CompletableFuture<Void>();
        request.bodyPublisher().orElseThrow().subscribe(
            new Flow.Subscriber<ByteBuffer>() {
                public void onSubscribe(Flow.Subscription s) {
                    s.request(Long.MAX_VALUE);
                }

                public void onNext(ByteBuffer b) {
                    byte[] chunk = new byte[b.remaining()];
                    b.get(chunk);
                    bytes.writeBytes(chunk);
                }

                public void onError(Throwable e) {
                    done.completeExceptionally(e);
                }

                public void onComplete() {
                    done.complete(null);
                }
            }
        );
        done.get(2, TimeUnit.SECONDS);
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
