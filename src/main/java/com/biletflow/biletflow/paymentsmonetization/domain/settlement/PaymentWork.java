package com.biletflow.biletflow.paymentsmonetization.domain.settlement;

import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePayment;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.TicketOrderPayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payment process state and lifecycle guards. Financial transitions use the contained aggregates. */
public class PaymentWork {

    public UUID id;
    public String purpose;
    public UUID eventId;
    public UUID orderId;
    public long payerId;
    public long organizerId;
    public BigDecimal amount;
    public BigDecimal fee;
    public String invoiceId;
    public String callbackSecret;
    public Instant deadline;
    public boolean abandoned;
    public boolean reviewRequired;
    public String operation;
    public UUID operationId;
    public boolean operationAcknowledged;
    public String bankTransactionId;
    public String operationState;
    public Instant operationAt;
    public TicketOrderPayment payment;
    public ActivationFeePayment activationPayment;

    public boolean isTicket() {
        return "TICKET".equals(purpose);
    }

    public boolean charged() {
        return isTicket() ? payment.hasBeenCharged() : activationPayment.getPaidAt() != null;
    }

    public String status() {
        return isTicket() ? payment.getStatus().name() : activationPayment.getStatus().name();
    }

    public boolean uncertainMutation() {
        return "SENT".equals(operationState) || "UNKNOWN".equals(operationState);
    }

    public void abandon() {
        abandoned = true;
    }

    public boolean reusableActivation(Instant now) {
        return "PENDING".equals(status()) && now.isBefore(deadline) && !abandoned;
    }

    public void replaceActivationAttempt() {
        if (charged() && !"REFUNDED".equals(status())) throw new IllegalStateException(
            "Previous activation payment requires reconciliation"
        );
        abandon();
    }

    public void requireSessionAvailable(Instant now) {
        if (!"PENDING".equals(status()) || abandoned || !now.isBefore(deadline)) throw new IllegalStateException(
            "Payment is not available for a bank session"
        );
    }

    public void acknowledge(UUID expectedId, String expectedOperation) {
        if (expectedId.equals(operationId) && expectedOperation.equals(operation) && uncertainMutation()) operationAcknowledged = true;
    }

    public long pollDelaySeconds(Instant now) {
        return reviewRequired ? 300 : now.isAfter(deadline.plusSeconds(86400)) ? 3600 : 15;
    }

    public void resetOperation() {
        operationAcknowledged = false;
        operation = null;
        operationId = null;
        operationState = null;
        operationAt = null;
    }

    public UUID requestTicketRefund(String reason, Instant now) {
        if (!isTicket()) throw new IllegalStateException("Activation fee refund policy is not part of ticket refunds");
        var current = payment.getRefund();
        if (
            current.isPresent() &&
            current.orElseThrow().getStatus() != com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.RefundStatus.FAILED
        ) return current.orElseThrow().getId().value();
        if (reason == null || reason.isBlank() || reason.length() > 200) throw new IllegalArgumentException(
            "Refund reason is required, max 200 characters"
        );
        var refund = payment.requestRefund(reason, now);
        resetOperation();
        return refund.getId().value();
    }

    public void requireSameRequest(UUID event, long payer, BigDecimal requestedAmount) {
        if (!eventId.equals(event) || payerId != payer || amount.compareTo(requestedAmount) != 0) throw new IllegalStateException(
            "Payment idempotency conflict"
        );
    }
}
