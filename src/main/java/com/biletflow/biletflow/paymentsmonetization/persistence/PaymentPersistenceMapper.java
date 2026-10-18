package com.biletflow.biletflow.paymentsmonetization.persistence;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import org.springframework.stereotype.Component;

@Component
public class PaymentPersistenceMapper {

    public PaymentWork domain(PaymentWorkJpaEntity entity, RefundJpaEntity current) {
        PaymentWork w = new PaymentWork();
        w.id = entity.id;
        w.purpose = entity.purpose;
        w.eventId = entity.eventId;
        w.orderId = entity.orderId;
        w.payerId = entity.payerId;
        w.organizerId = entity.organizerId;
        w.amount = entity.amount;
        w.fee = entity.fee;
        w.invoiceId = entity.invoiceId;
        w.callbackSecret = entity.callbackSecret;
        w.deadline = entity.deadline;
        w.abandoned = entity.abandoned;
        w.reviewRequired = entity.reviewRequired;
        w.operation = entity.operation;
        w.operationId = entity.operationId;
        w.operationState = entity.operationState;
        w.operationAt = entity.operationAt;
        w.operationAcknowledged = entity.operationAcknowledged;
        w.bankTransactionId = entity.bankTransactionId;
        Refund refund =
            current == null
                ? null
                : new Refund(
                      new RefundId(current.id),
                      current.reason,
                      current.requestedAt,
                      RefundStatus.valueOf(current.status),
                      current.reference,
                      current.failure,
                      current.completedAt
                  );
        if (w.isTicket()) w.payment = new TicketOrderPayment(
            new TicketOrderPaymentId(entity.id),
            entity.orderId,
            entity.eventId,
            entity.organizerId,
            Money.kzt(entity.amount),
            TicketOrderPaymentStatus.valueOf(entity.status),
            entity.providerReference,
            entity.paidAt,
            entity.failedAt,
            entity.failureReason,
            refund
        );
        else w.activationPayment = new ActivationFeePayment(
            new ActivationFeePaymentId(entity.id),
            entity.eventId,
            entity.organizerId,
            Money.kzt(entity.amount),
            ActivationFeePaymentStatus.valueOf(entity.status),
            entity.providerReference,
            entity.paidAt,
            entity.failedAt,
            entity.failureReason
        );
        return w;
    }

    public void update(PaymentWork work, PaymentWorkJpaEntity entity) {
        entity.id = work.id;
        entity.purpose = work.purpose;
        entity.eventId = work.eventId;
        entity.orderId = work.orderId;
        entity.payerId = work.payerId;
        entity.organizerId = work.organizerId;
        entity.amount = work.amount;
        entity.fee = work.fee;
        entity.invoiceId = work.invoiceId;
        entity.callbackSecret = work.callbackSecret;
        entity.deadline = work.deadline;
        entity.abandoned = work.abandoned;
        entity.reviewRequired = work.reviewRequired;
        entity.operation = work.operation;
        entity.operationId = work.operationId;
        entity.operationState = work.operationState;
        entity.operationAt = work.operationAt;
        entity.operationAcknowledged = work.operationAcknowledged;
        entity.bankTransactionId = work.bankTransactionId;
        entity.status = work.status();
        entity.providerReference = work.isTicket() ? work.payment.getProviderReference() : work.activationPayment.getProviderReference();
        entity.paidAt = work.isTicket() ? work.payment.getPaidAt() : work.activationPayment.getPaidAt();
        entity.failedAt = work.isTicket() ? work.payment.getFailedAt() : work.activationPayment.getFailedAt();
        entity.failureReason = work.isTicket() ? work.payment.getFailureReason() : work.activationPayment.getFailureReason();
        entity.currentRefundId = work.isTicket()
            ? work.payment
                  .getRefund()
                  .map(refund -> refund.getId().value())
                  .orElse(null)
            : null;
    }

    public void update(Refund refund, java.util.UUID paymentId, RefundJpaEntity entity) {
        entity.id = refund.getId().value();
        entity.paymentId = paymentId;
        entity.reason = refund.getReason();
        entity.requestedAt = refund.getRequestedAt();
        entity.status = refund.getStatus().name();
        entity.reference = refund.getProviderReference();
        entity.failure = refund.getFailureReason();
        entity.completedAt = refund.getCompletedAt();
    }
}
