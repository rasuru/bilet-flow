package com.biletflow.biletflow.paymentsmonetization.infrastructure.halyk;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentGateway;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentProviderUnavailableException;
import com.biletflow.biletflow.paymentsmonetization.config.HalykProperties;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperation;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.SettlementEvidence;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.SettlementStatus;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class HalykGateway implements PaymentGateway {

    private static final String OAUTH = "https://test-epay-oauth.epayment.kz/oauth2/token";
    private static final String API = "https://test-epay-api.epayment.kz";
    private static final String SCRIPT = "https://test-epay.epayment.kz/payform/payment-api.js";
    private static final String SCOPE = "webapi usermanagement email_send verification statement statistics payment";
    private final HalykHttp http;
    private final HalykProperties config;
    private final ObjectMapper json;

    public HalykGateway(HalykProperties config, ObjectMapper json, HalykHttp http) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.json = Objects.requireNonNull(json, "json cannot be null");
        this.http = Objects.requireNonNull(http, "http cannot be null");
    }

    @Override
    public boolean acceptsInvoiceId(String invoiceId) {
        return invoiceId != null && invoiceId.matches("[0-9]{6,15}");
    }

    @Override
    public void validateAmount(java.math.BigDecimal amount) {
        if (amount == null || amount.compareTo(java.math.BigDecimal.TEN) < 0 || amount.scale() > 2) throw new IllegalArgumentException(
            "Halyk sandbox payments must be at least 10 KZT with at most two decimals"
        );
    }

    @Override
    public Session session(PaymentWork work) {
        String callback = config.getPublicBaseUrl() + "/api/payments/halyk/callback";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("invoiceID", work.getInvoiceId());
        fields.put("amount", work.getAmount().toPlainString());
        fields.put("currency", "KZT");
        fields.put("secret_hash", work.getCallbackSecret());
        fields.put("postLink", callback);
        fields.put("failurePostLink", callback);
        Map<String, Object> auth = token(fields);
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("invoiceId", work.getInvoiceId());
        form.put("amount", work.getAmount());
        form.put("currency", "KZT");
        form.put("terminal", config.getTerminalId());
        form.put("description", "BiletFlow SANDBOX " + work.getId());
        form.put("language", "eng");
        form.put("backLink", config.getReturnUrl());
        form.put("failureBackLink", config.getReturnUrl());
        form.put("postLink", callback);
        form.put("failurePostLink", callback);
        form.put("auth", auth);
        form.put("data", json.writeValueAsString(Map.of("biletflowPaymentId", work.getId().toString())));
        // Neither merchant ClientSecret nor callback secret is returned to the browser.
        return new Session(Map.of(), SCRIPT, Map.copyOf(form));
    }

    @Override
    public SettlementEvidence inspect(String invoiceId) {
        String access = accessToken();
        String body = send(
            HttpRequest.newBuilder(URI.create(API + "/check-status/payment/transaction/" + invoiceId))
                .header("Authorization", "Bearer " + access)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build()
        );
        var root = json.readTree(body);
        String code = root.path("resultCode").asString("");
        if ("102".equals(code)) return new SettlementEvidence(invoiceId, null, null, null, false, SettlementStatus.NOT_FOUND, null);
        if ("107".equals(code)) return new SettlementEvidence(invoiceId, null, null, null, false, SettlementStatus.PENDING, null);
        var t = root.path("transaction");
        if (t.isMissingNode() || t.isNull()) {
            // A failed status lookup is not proof that an individual payment failed.
            throw new PaymentProviderUnavailableException();
        }
        if (!"100".equals(code) && !"101".equals(code)) throw new PaymentProviderUnavailableException();
        var data = t.path("data");
        var correlation = data.isString() ? json.readTree(data.asString("")) : data;
        return new SettlementEvidence(
            t.path("invoiceID").asString(""),
            t.path("id").asString(""),
            t.path("amount").decimalValue(null),
            t.path("currency").asString(""),
            config.getTerminalId().equals(t.path("terminalID").asString("")),
            normalize(t.path("statusName").asString("")),
            correlation.path("biletflowPaymentId").asString("")
        );
    }

    @Override
    public void mutate(String transactionId, PaymentOperation operation, String externalId) {
        // Validate provider-supplied IDs before interpolating into paths.
        UUID.fromString(transactionId);
        String operationCode = switch (operation) {
            case CAPTURE -> "charge";
            case VOID -> "cancel";
            case REFUND -> "refund";
        };
        String suffix = operation == PaymentOperation.REFUND ? "?externalID=" + encode(externalId) : "";
        send(
            HttpRequest.newBuilder(URI.create(API + "/operation/" + transactionId + "/" + operationCode + suffix))
                .header("Authorization", "Bearer " + accessToken())
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build()
        );
        // A 200 response is acknowledgement only. Completion always comes from inspect().
    }

    private SettlementStatus normalize(String code) {
        return switch (code.toUpperCase(java.util.Locale.ROOT)) {
            case "CHARGE" -> SettlementStatus.CAPTURED;
            case "AUTH" -> SettlementStatus.AUTHORIZED;
            case "REFUND" -> SettlementStatus.REFUNDED;
            case "CANCEL", "CANCEL_OLD" -> SettlementStatus.VOIDED;
            case "FAILED", "REJECT", "3D" -> SettlementStatus.DECLINED;
            case "NEW", "FINGERPRINT" -> SettlementStatus.PENDING;
            default -> SettlementStatus.UNKNOWN;
        };
    }

    private String accessToken() {
        return String.valueOf(token(Map.of()).get("access_token"));
    }

    private Map<String, Object> token(Map<String, String> extra) {
        if (config.getClientSecret().isBlank()) throw new IllegalStateException(
            "Configure BILETFLOW_PAYMENTS_HALYK_CLIENT_SECRET for Halyk sandbox"
        );
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("grant_type", "client_credentials");
        fields.put("scope", SCOPE);
        fields.put("client_id", config.getClientId());
        fields.put("client_secret", config.getClientSecret());
        fields.put("terminal", config.getTerminalId());
        fields.putAll(extra);
        // Official API documents form-data, so use multipart rather than assuming urlencoded support.
        String boundary = "BiletFlow" + UUID.randomUUID().toString().replace("-", "");
        String body =
            fields
                .entrySet()
                .stream()
                .map(
                    e -> "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n" + e.getValue() + "\r\n"
                )
                .collect(Collectors.joining()) +
            "--" +
            boundary +
            "--\r\n";
        String response = send(
            HttpRequest.newBuilder(URI.create(OAUTH))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build()
        );
        Map<String, Object> value = json.readValue(response, new TypeReference<Map<String, Object>>() {});
        if (!(value.get("access_token") instanceof String access) || access.isBlank()) throw new PaymentProviderUnavailableException();
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : List.of("access_token", "expires_in", "refresh_token", "scope", "token_type")) {
            if (value.containsKey(key)) safe.put(key, value.get(key));
        }
        return safe;
    }

    private String send(HttpRequest request) {
        return http.send(request);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
