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
    String paymentId,
    BigDecimal originalAmount,
    BigDecimal refundedAmount
) {
    /** Providers or observations without refund totals cannot establish full-refund scope. */
    public SettlementEvidence(
        String invoiceId,
        String transactionId,
        BigDecimal amount,
        String currency,
        boolean merchantVerified,
        SettlementStatus status,
        String paymentId
    ) {
        this(invoiceId, transactionId, amount, currency, merchantVerified, status, paymentId, null, null);
    }

    public boolean provesFullRefundOf(BigDecimal expected) {
        return (
            status == SettlementStatus.REFUNDED &&
            originalAmount != null &&
            refundedAmount != null &&
            originalAmount.compareTo(expected) == 0 &&
            refundedAmount.compareTo(expected) == 0
        );
    }

    public boolean contradictsFullRefundOf(BigDecimal expected) {
        return (
            status == SettlementStatus.REFUNDED &&
            ((originalAmount != null && originalAmount.compareTo(expected) != 0) ||
                (refundedAmount != null && refundedAmount.compareTo(expected) != 0))
        );
    }
}
