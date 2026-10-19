package com.biletflow.biletflow.paymentsmonetization.domain.settlement;

public enum OperationResolution {
    FULL_REFUND_CONFIRMED_COMPLETED,
    CLEAR_REVIEW,
    CONFIRMED_NOT_EXECUTED_RETRY,
    REFUND_CONFIRMED_REJECTED,
}
