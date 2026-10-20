package com.biletflow.biletflow.paymentsmonetization.ohs.dto;

import java.util.UUID;

public record PaymentView(
    UUID id,
    UUID orderId,
    String status,
    UUID refundId,
    String refundStatus,
    boolean reviewRequired,
    String bankOperation,
    UUID bankOperationId,
    String bankOperationState,
    boolean sandbox
) {}
