package com.biletflow.biletflow.paymentsmonetization.domain.settlement;

import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.SettlementStatus;
import java.math.BigDecimal;

public record SettlementEvidence(
    String invoiceId,
    String transactionId,
    BigDecimal amount,
    String currency,
    boolean merchantVerified,
    SettlementStatus status,
    String paymentId
) {}
