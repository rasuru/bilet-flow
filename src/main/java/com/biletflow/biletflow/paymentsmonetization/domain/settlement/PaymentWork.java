package com.biletflow.biletflow.paymentsmonetization.domain.settlement;

import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePayment;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Payment process state. Financial transitions use the contained aggregates. */
public class PaymentWork {

    private final UUID id;
    private final PaymentPurpose purpose;
    private final UUID eventId;
    private final UUID orderId;
    private final long payerId;
    private final long organizerId;
    private final BigDecimal amount;
    private final BigDecimal fee;
    private final String invoiceId;
    private final String callbackSecret;
    private final Instant deadline;
    private boolean abandoned;
    private boolean reviewRequired;
    private PaymentOperation operation;
    private UUID operationId;
    private boolean operationAcknowledged;
    private String bankTransactionId;
    private PaymentOperationState operationState;
    private Instant operationAt;
    private final TicketOrderPayment payment;
    private final ActivationFeePayment activationPayment;

    /** Reconstitution constructor; identity and pricing remain immutable across transitions. */
    public PaymentWork(
        UUID id,
        PaymentPurpose purpose,
        UUID eventId,
        UUID orderId,
        long payerId,
        long organizerId,
        BigDecimal amount,
        BigDecimal fee,
        String invoiceId,
        String callbackSecret,
        Instant deadline,
        boolean abandoned,
        boolean reviewRequired,
        PaymentOperation operation,
        UUID operationId,
        boolean operationAcknowledged,
        String bankTransactionId,
        PaymentOperationState operationState,
        Instant operationAt,
        TicketOrderPayment payment,
        ActivationFeePayment activationPayment
    ) {
        this.id = id;
        this.purpose = purpose;
        this.eventId = eventId;
        this.orderId = orderId;
        this.payerId = payerId;
        this.organizerId = organizerId;
        this.amount = amount;
        this.fee = fee;
        this.invoiceId = invoiceId;
        this.callbackSecret = callbackSecret;
        this.deadline = deadline;
        this.abandoned = abandoned;
        this.reviewRequired = reviewRequired;
        this.operation = operation;
        this.operationId = operationId;
        this.operationAcknowledged = operationAcknowledged;
        this.bankTransactionId = bankTransactionId;
        this.operationState = operationState;
        this.operationAt = operationAt;
        this.payment = payment;
        this.activationPayment = activationPayment;

        Objects.requireNonNull(id, "Payment id is required");
        Objects.requireNonNull(purpose, "Payment purpose is required");
        Objects.requireNonNull(eventId, "Event id is required");
        Objects.requireNonNull(amount, "Amount is required");
        Objects.requireNonNull(fee, "Fee is required");
        Objects.requireNonNull(deadline, "Deadline is required");
        if (payerId <= 0 || organizerId <= 0 || amount.signum() <= 0 || fee.signum() < 0) throw new IllegalArgumentException(
            "Invalid payment identity or pricing"
        );
        if (
            invoiceId == null || invoiceId.isBlank() || callbackSecret == null || callbackSecret.isBlank()
        ) throw new IllegalArgumentException("Invoice and callback authentication are required");
        if (isTicket()) {
            Objects.requireNonNull(orderId, "Ticket payment requires an order");
            Objects.requireNonNull(payment, "Ticket payment is required");
            if (activationPayment != null || !id.equals(payment.getId().value())) throw new IllegalArgumentException(
                "Conflicting ticket payment identity"
            );
        } else {
            Objects.requireNonNull(activationPayment, "Activation payment is required");
            if (orderId != null || payment != null || !id.equals(activationPayment.getId().value())) throw new IllegalArgumentException(
                "Conflicting activation payment identity"
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public PaymentPurpose getPurpose() {
        return purpose;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public long getPayerId() {
        return payerId;
    }

    public long getOrganizerId() {
        return organizerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getCallbackSecret() {
        return callbackSecret;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public boolean isAbandoned() {
        return abandoned;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public PaymentOperation getOperation() {
        return operation;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public boolean isOperationAcknowledged() {
        return operationAcknowledged;
    }

    public String getBankTransactionId() {
        return bankTransactionId;
    }

    public PaymentOperationState getOperationState() {
        return operationState;
    }

    public Instant getOperationAt() {
        return operationAt;
    }

    public TicketOrderPayment getPayment() {
        return payment;
    }

    public ActivationFeePayment getActivationPayment() {
        return activationPayment;
    }

    public boolean isTicket() {
        return purpose == PaymentPurpose.TICKET;
    }

    public boolean hasBeenCharged() {
        return isTicket() ? payment.hasBeenCharged() : activationPayment.getPaidAt() != null;
    }

    public PaymentStatus getStatus() {
        return PaymentStatus.valueOf(isTicket() ? payment.getStatus().name() : activationPayment.getStatus().name());
    }

    public boolean isMutationUncertain() {
        return operationState == PaymentOperationState.SENT || operationState == PaymentOperationState.UNKNOWN;
    }

    public void abandon() {
        abandoned = true;
    }

    public boolean reusableActivation(Instant now) {
        return getStatus() == PaymentStatus.PENDING && now.isBefore(deadline) && !abandoned;
    }

    public void replaceActivationAttempt() {
        if (hasBeenCharged() && getStatus() != PaymentStatus.REFUNDED) throw new IllegalStateException(
            "Previous activation payment requires reconciliation"
        );
        abandon();
    }

    public void requireSessionAvailable(Instant now) {
        if (getStatus() != PaymentStatus.PENDING || abandoned || !now.isBefore(deadline)) throw new IllegalStateException(
            "Payment is not available for a bank session"
        );
    }

    public void acknowledge(UUID expectedId, PaymentOperation expectedOperation) {
        if (expectedId.equals(operationId) && expectedOperation == operation && isMutationUncertain()) operationAcknowledged = true;
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

    public void requireReview() {
        reviewRequired = true;
    }

    public void clearReview() {
        reviewRequired = false;
    }

    public void recordVerifiedTransaction(String reference) {
        bankTransactionId = Objects.requireNonNull(reference);
    }

    public void confirmOperation() {
        operationState = PaymentOperationState.CONFIRMED;
    }

    public void rejectOperation() {
        operationState = PaymentOperationState.REJECTED;
        reviewRequired = false;
    }

    public void markOperationUnknown() {
        if (!isMutationUncertain()) throw new IllegalStateException("No unresolved bank operation");
        operationState = PaymentOperationState.UNKNOWN;
    }

    public void allowRetryAfterConfirmedNonExecution() {
        if (!isMutationUncertain()) throw new IllegalStateException("No unresolved bank operation");
        operationAcknowledged = false;
        operationState = null;
        operation = null;
        operationAt = null;
        reviewRequired = false;
    }

    public void finishCaptureOperation() {
        operationState = null;
        operationAcknowledged = false;
        operation = null;
    }

    public void prepareActivationCompensation() {
        operationAcknowledged = false;
        operation = null;
        operationState = null;
    }

    public UUID recordDispatch(PaymentOperation requested, Instant now) {
        if (isMutationUncertain()) throw new IllegalStateException("Previous bank operation is unresolved");
        operationAcknowledged = false;
        operation = Objects.requireNonNull(requested);
        operationState = PaymentOperationState.SENT;
        operationAt = now;
        operationId =
            requested == PaymentOperation.REFUND && isTicket() ? payment.getRefund().orElseThrow().getId().value() : UUID.randomUUID();
        return operationId;
    }

    public UUID requestTicketRefund(String reason, Instant now) {
        if (!isTicket()) throw new IllegalStateException("Activation fee refund policy is not part of ticket refunds");
        var current = payment.getRefund();
        if (current.isPresent() && current.orElseThrow().getStatus() != RefundStatus.FAILED) return current.orElseThrow().getId().value();
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
