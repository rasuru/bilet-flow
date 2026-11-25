package com.biletflow.biletflow.paymentsmonetization.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperation;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.SettlementEvidence;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Payment provider operations and their normalized settlement evidence. */
public interface PaymentGateway {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Session(Map<String, Object> auth, String scriptUrl, Map<String, Object> paymentObject, UUID mockPaymentId) {
        public Session(Map<String, Object> auth, String scriptUrl, Map<String, Object> paymentObject) {
            this(auth, scriptUrl, paymentObject, null);
        }
    }

    default String allocateInvoice(BigDecimal amount, Supplier<String> halykInvoice) {
        validateAmount(amount);
        return halykInvoice.get();
    }

    boolean acceptsInvoiceId(String invoiceId);
    void validateAmount(BigDecimal amount);
    Session session(PaymentWork work);
    SettlementEvidence inspect(String invoiceId);
    void mutate(String transactionId, PaymentOperation operation, String externalId);

    default void mutate(PaymentWork work, String transactionId, PaymentOperation operation, String externalId) {
        mutate(transactionId, operation, externalId);
    }
}
