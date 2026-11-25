package com.biletflow.biletflow.paymentsmonetization.application.view;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.OperationResolution;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperation;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperationState;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    boolean sandbox,
    UUID refundId,
    String refundStatus,
    String bankOperation,
    UUID bankOperationId,
    Instant bankOperationAt,
    List<OperationResolution> resolutions
) {
    public static AdminPaymentView from(PaymentWork work) {
        boolean ticket = work.isTicket();
        var refund = ticket ? work.getPayment().getRefund().orElse(null) : null;
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
            needsReview(work),
            work.getOperationState() == null ? null : work.getOperationState().name(),
            true,
            refund == null ? null : refund.getId().value(),
            refund == null ? null : refund.getStatus().name(),
            work.getOperation() == null ? null : work.getOperation().name(),
            work.getOperationId(),
            work.getOperationAt(),
            resolutions(work)
        );
    }

    private static boolean needsReview(PaymentWork work) {
        return work.isReviewRequired() || work.getOperationState() == PaymentOperationState.UNKNOWN;
    }

    private static List<OperationResolution> resolutions(PaymentWork work) {
        if (!needsReview(work)) return List.of();
        var resolutions = new ArrayList<OperationResolution>();
        if (work.isReviewRequired()) resolutions.add(OperationResolution.CLEAR_REVIEW);
        if (work.getBankTransactionId() != null) resolutions.add(OperationResolution.FULL_REFUND_CONFIRMED_COMPLETED);
        if (work.isMutationUncertain()) {
            resolutions.add(OperationResolution.CONFIRMED_NOT_EXECUTED_RETRY);
            if (work.isTicket() && work.getOperation() == PaymentOperation.REFUND && work.hasDispatchedRefund()) {
                resolutions.add(OperationResolution.REFUND_CONFIRMED_REJECTED);
            }
        }
        return List.copyOf(resolutions);
    }
}
