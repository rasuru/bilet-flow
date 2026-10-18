package com.biletflow.biletflow.paymentsmonetization.persistence;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Explicit schema for persistence, independent of domain constructors/getter serialization. */
record PaymentSnapshot(
    UUID id,
    String purpose,
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
    String operation,
    UUID operationId,
    String operationState,
    Instant operationAt,
    String status,
    String providerReference,
    Instant paidAt,
    Instant failedAt,
    String failureReason,
    RefundData refund,
    boolean operationAcknowledged,
    String bankTransactionId
) {
    record RefundData(
        UUID id,
        String reason,
        Instant requestedAt,
        RefundStatus status,
        String reference,
        String failure,
        Instant completedAt
    ) {
        static RefundData of(Refund r) {
            return new RefundData(
                r.getId().value(),
                r.getReason(),
                r.getRequestedAt(),
                r.getStatus(),
                r.getProviderReference(),
                r.getFailureReason(),
                r.getCompletedAt()
            );
        }

        Refund domain() {
            return new Refund(new RefundId(id), reason, requestedAt, status, reference, failure, completedAt);
        }
    }

    static PaymentSnapshot of(PaymentWork w) {
        var p = w.payment;
        var a = w.activationPayment;
        return new PaymentSnapshot(
            w.id,
            w.purpose,
            w.eventId,
            w.orderId,
            w.payerId,
            w.organizerId,
            w.amount,
            w.fee,
            w.invoiceId,
            w.callbackSecret,
            w.deadline,
            w.abandoned,
            w.reviewRequired,
            w.operation,
            w.operationId,
            w.operationState,
            w.operationAt,
            w.status(),
            w.isTicket() ? p.getProviderReference() : a.getProviderReference(),
            w.isTicket() ? p.getPaidAt() : a.getPaidAt(),
            w.isTicket() ? p.getFailedAt() : a.getFailedAt(),
            w.isTicket() ? p.getFailureReason() : a.getFailureReason(),
            w.isTicket() ? p.getRefund().map(RefundData::of).orElse(null) : null,
            w.operationAcknowledged,
            w.bankTransactionId
        );
    }

    PaymentWork domain() {
        PaymentWork w = new PaymentWork();
        w.id = id;
        w.purpose = purpose;
        w.eventId = eventId;
        w.orderId = orderId;
        w.payerId = payerId;
        w.organizerId = organizerId;
        w.amount = amount;
        w.fee = fee;
        w.invoiceId = invoiceId;
        w.callbackSecret = callbackSecret;
        w.deadline = deadline;
        w.abandoned = abandoned;
        w.reviewRequired = reviewRequired;
        w.operation = operation;
        w.operationId = operationId;
        w.operationState = operationState;
        w.operationAt = operationAt;
        w.operationAcknowledged = operationAcknowledged;
        w.bankTransactionId = bankTransactionId;
        if (w.isTicket()) w.payment = new TicketOrderPayment(
            new TicketOrderPaymentId(id),
            orderId,
            eventId,
            organizerId,
            Money.kzt(amount),
            TicketOrderPaymentStatus.valueOf(status),
            providerReference,
            paidAt,
            failedAt,
            failureReason,
            refund == null ? null : refund.domain()
        );
        else w.activationPayment = new ActivationFeePayment(
            new ActivationFeePaymentId(id),
            eventId,
            organizerId,
            Money.kzt(amount),
            ActivationFeePaymentStatus.valueOf(status),
            providerReference,
            paidAt,
            failedAt,
            failureReason
        );
        return w;
    }
}
