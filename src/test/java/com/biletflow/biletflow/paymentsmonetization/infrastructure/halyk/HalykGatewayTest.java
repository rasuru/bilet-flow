package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentProviderUnavailableException;
import com.biletflow.biletflow.paymentsmonetization.config.HalykProperties;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperation;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentTerms;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.SettlementStatus;
import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
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
        "https://example.com/return"
    );
    private final Deque<String> responses = new ArrayDeque<>();
    private final List<HttpRequest> requests = new ArrayList<>();

    private HalykGateway gateway() {
        return new HalykGateway(
            config,
            JsonMapper.builder().build(),
            request -> {
                requests.add(request);
                return responses.removeFirst();
            },
            Clock.systemUTC()
        );
    }

    @Test
    void paymentTokenIsBoundAndBrowserObjectContainsNoMerchantSecret() throws Exception {
        responses.add(TOKEN);
        var now = Instant.now();
        var work = new PaymentTerms(new BigDecimal("1000"), BigDecimal.ZERO).ticket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            7,
            new BigDecimal("123.45"),
            now.plusSeconds(600),
            now,
            "123456",
            "bank-only-secret"
        );
        var session = gateway().session(work);
        String body = body(requests.get(0));
        assertTrue(requests.get(0).uri().toString().equals("https://test-epay-oauth.epayment.kz/oauth2/token"));
        for (String field : List.of("invoiceID", "amount", "currency", "terminal", "secret_hash", "postLink", "failurePostLink"))
            assertTrue(body.contains("name=\"" + field + "\""));
        assertTrue(body.contains("123456") && body.contains("123.45") && body.contains("bank-only-secret"));
        assertEquals("https://test-epay.epayment.kz/payform/payment-api.js", session.scriptUrl());
        assertFalse(session.paymentObject().toString().contains("merchant-secret"));
        assertFalse(session.paymentObject().toString().contains("bank-only-secret"));
        assertTrue(session.paymentObject().get("data").toString().contains(work.getId().toString()));
    }

    @Test
    void resultCode100IsNotNecessarilyCaptured() {
        UUID payment = UUID.randomUUID();
        responses.add(TOKEN);
        responses.add(transaction("AUTH", payment));
        var observation = gateway().inspect("123456");
        assertEquals(SettlementStatus.AUTHORIZED, observation.status());
        assertTrue(observation.merchantVerified());
        assertEquals(new BigDecimal("123.45"), observation.amount());
        assertEquals(payment.toString(), observation.paymentId());
        assertTrue(requests.get(1).uri().toString().endsWith("/check-status/payment/transaction/123456"));
    }

    @Test
    void wrongTerminalIsReportedAsUnverifiedMerchant() {
        responses.add(TOKEN);
        responses.add(transaction("CHARGE", UUID.randomUUID()).replace(config.getTerminalId(), UUID.randomUUID().toString()));
        assertFalse(gateway().inspect("123456").merchantVerified());
    }

    @Test
    void notFoundAndTransientErrorsCannotBecomePaymentFailure() {
        responses.add(TOKEN);
        responses.add("{\"resultCode\":\"102\",\"transaction\":null}");
        assertEquals(SettlementStatus.NOT_FOUND, gateway().inspect("123456").status());
        responses.add(TOKEN);
        responses.add("{\"resultCode\":\"103\",\"transaction\":null}");
        assertThrows(PaymentProviderUnavailableException.class, () -> gateway().inspect("123456"));
    }

    @Test
    void fullRefundUsesTransactionIdAndStableExternalId() {
        responses.add(TOKEN);
        responses.add("");
        UUID transaction = UUID.randomUUID(),
            refund = UUID.randomUUID();
        gateway().mutate(transaction.toString(), PaymentOperation.REFUND, refund.toString());
        var request = requests.get(1);
        assertEquals("POST", request.method());
        assertEquals(
            "https://test-epay-api.epayment.kz/operation/" + transaction + "/refund?externalID=" + refund,
            request.uri().toString()
        );
        assertEquals("Bearer scoped-token", request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void refundStatusIncludesIndependentOriginalAndRefundedTotals() {
        responses.add(TOKEN);
        responses.add(
            transaction("REFUND", UUID.randomUUID()).replace(
                "\"amount\":123.45",
                "\"amount\":0,\"orgAmount\":123.45,\"payoutAmount\":123.45"
            )
        );
        var observed = gateway().inspect("123456");
        assertEquals(BigDecimal.ZERO, observed.amount());
        assertEquals(new BigDecimal("123.45"), observed.originalAmount());
        assertEquals(new BigDecimal("123.45"), observed.refundedAmount());
        assertTrue(observed.provesFullRefundOf(new BigDecimal("123.45")));
    }

    @Test
    void partialRefundTotalsCannotBecomeFullRefundEvidence() {
        responses.add(TOKEN);
        responses.add(
            transaction("REFUND", UUID.randomUUID()).replace(
                "\"amount\":123.45",
                "\"amount\":113.45,\"orgAmount\":123.45,\"payoutAmount\":10"
            )
        );
        var observed = gateway().inspect("123456");
        assertFalse(observed.provesFullRefundOf(new BigDecimal("123.45")));
        assertTrue(observed.contradictsFullRefundOf(new BigDecimal("123.45")));
    }

    @Test
    void merchantTokenIsReusedAcrossStatusRequestsButNotForTheHostedPaymentForm() {
        responses.add(TOKEN);
        responses.add(transaction("CHARGE", UUID.randomUUID()));
        responses.add(transaction("CHARGE", UUID.randomUUID()));
        var gateway = gateway();
        gateway.inspect("123456");
        gateway.inspect("123456");
        assertEquals(3, requests.size(), "Two status checks share one merchant token");
        responses.add(TOKEN);
        var now = Instant.now();
        var work = new PaymentTerms(BigDecimal.TEN, BigDecimal.ZERO).ticket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            7,
            BigDecimal.TEN,
            now.plusSeconds(60),
            now,
            "123456",
            "callback-secret"
        );
        gateway.session(work);
        assertEquals(4, requests.size(), "The hosted form still gets a separately scoped token");
    }

    @Test
    void expiredMerchantTokenIsRefreshedBeforeItCanDelayStatusChecks() {
        var clock = new MutableClock();
        var responses = new ArrayDeque<String>();
        var requests = new ArrayList<HttpRequest>();
        var gateway = new HalykGateway(
            config,
            JsonMapper.builder().build(),
            request -> {
                requests.add(request);
                return responses.removeFirst();
            },
            clock
        );
        responses.add("{\"access_token\":\"first\",\"expires_in\":60}");
        responses.add(transaction("CHARGE", UUID.randomUUID()));
        gateway.inspect("123456");
        clock.advance(31);
        responses.add("{\"access_token\":\"second\",\"expires_in\":60}");
        responses.add(transaction("CHARGE", UUID.randomUUID()));
        gateway.inspect("123456");
        assertEquals("Bearer second", requests.get(3).headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void unauthorizedTokenIsInvalidatedWithoutRepeatingTheFailedRequest() {
        var calls = new ArrayList<HttpRequest>();
        var gateway = new HalykGateway(
            config,
            JsonMapper.builder().build(),
            request -> {
                calls.add(request);
                if (request.uri().getPath().equals("/oauth2/token")) return TOKEN;
                throw new PaymentProviderUnavailableException(
                    PaymentProviderUnavailableException.Kind.HTTP,
                    401,
                    PaymentProviderUnavailableException.Phase.STATUS
                );
            },
            Clock.systemUTC()
        );
        assertThrows(PaymentProviderUnavailableException.class, () -> gateway.inspect("123456"));
        assertEquals(2, calls.size(), "A failed request is not silently repeated");
        assertThrows(PaymentProviderUnavailableException.class, () -> gateway.inspect("123456"));
        assertEquals(4, calls.size(), "The next attempt obtains a fresh token");
    }

    private static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-20T12:00:00Z");

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(now, zone);
        }
    }

    private String transaction(String status, UUID payment) {
        return (
            "{\"resultCode\":\"100\",\"transaction\":{\"id\":\"00000000-0000-0000-0000-000000000001\",\"invoiceID\":\"123456\",\"amount\":123.45,\"currency\":\"KZT\",\"terminalID\":\"" +
            config.getTerminalId() +
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
