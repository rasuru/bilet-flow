package com.biletflow.biletflow.paymentsmonetization.application;

import java.math.BigDecimal;
import java.util.Map;

/** Anti-corruption port; only the Halyk adapter knows endpoints and bank JSON. */
public interface PaymentGateway {
    record Session(Map<String, Object> auth, String scriptUrl, Map<String, Object> paymentObject) {}

    record Observation(
        String invoiceId,
        String transactionId,
        BigDecimal amount,
        String currency,
        String terminalId,
        com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.SettlementStatus status,
        String paymentId
    ) {}

    Session session(PaymentWork work);
    Observation inspect(String invoiceId);
    void mutate(String transactionId, String operation, String externalId);
}
