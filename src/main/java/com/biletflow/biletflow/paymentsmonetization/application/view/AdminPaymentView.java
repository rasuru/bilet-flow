package com.biletflow.biletflow.paymentsmonetization.application.view;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminPaymentView(
    UUID id,
    String purpose,
    UUID eventId,
    UUID orderId,
    long payerId,
    long organizerId,
    BigDecimal amountKzt,
    BigDecimal estimatedFeeKzt,
    String status,
    Instant paidAt,
    Instant failedAt,
    String failureReason,
    boolean reviewRequired,
    String operationState,
    boolean sandbox
) {
    public static AdminPaymentView from(PaymentWork work) {
        boolean ticket = work.isTicket();
        return new AdminPaymentView(
            work.getId(),
            work.getPurpose().name(),
            work.getEventId(),
            work.getOrderId(),
            work.getPayerId(),
            work.getOrganizerId(),
            work.getAmount(),
            work.getFee(),
            work.getStatus().name(),
            ticket ? work.getPayment().getPaidAt() : work.getActivationPayment().getPaidAt(),
            ticket ? work.getPayment().getFailedAt() : work.getActivationPayment().getFailedAt(),
            ticket ? work.getPayment().getFailureReason() : work.getActivationPayment().getFailureReason(),
            work.isReviewRequired(),
            work.getOperationState() == null ? null : work.getOperationState().name(),
            true
        );
    }
}
