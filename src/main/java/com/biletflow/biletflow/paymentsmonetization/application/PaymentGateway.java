package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.SettlementEvidence;
import java.math.BigDecimal;
import java.util.Map;

/** Anti-corruption port; only the Halyk adapter knows endpoints and bank JSON. */
public interface PaymentGateway {
    record Session(Map<String, Object> auth, String scriptUrl, Map<String, Object> paymentObject) {}

    boolean acceptsInvoiceId(String invoiceId);
    void validateAmount(BigDecimal amount);
    Session session(PaymentWork work);
    SettlementEvidence inspect(String invoiceId);
    void mutate(String transactionId, String operation, String externalId);
}
