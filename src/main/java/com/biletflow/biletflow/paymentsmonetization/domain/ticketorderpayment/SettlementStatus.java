package com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment;

/** Financial facts in our language. Halyk response codes are translated by the adapter. */
public enum SettlementStatus {
    NOT_FOUND,
    PENDING,
    AUTHORIZED,
    CAPTURED,
    REFUNDED,
    VOIDED,
    DECLINED,
    UNKNOWN,
}
