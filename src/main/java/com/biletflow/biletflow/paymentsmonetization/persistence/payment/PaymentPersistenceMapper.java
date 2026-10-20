package com.biletflow.biletflow.paymentsmonetization.persistence.payment;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity.PaymentWorkJpaEntity;
import com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity.RefundJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentPersistenceMapper {

    public PaymentWork toDomain(PaymentWorkJpaEntity entity, RefundJpaEntity current) {
        Refund refund =
            current == null
                ? null
                : new Refund(
                      new RefundId(current.getId()),
                      current.getReason(),
                      current.getRequestedAt(),
                      current.getStatus(),
                      current.getReference(),
                      current.getFailure(),
                      current.getCompletedAt()
                  );
        PaymentPurpose purpose = entity.getPurpose();
        var ticket =
            purpose == PaymentPurpose.TICKET
                ? new TicketOrderPayment(
                      new TicketOrderPaymentId(entity.getId()),
                      entity.getOrderId(),
                      entity.getEventId(),
                      entity.getOrganizerId(),
                      Money.kzt(entity.getAmount()),
                      TicketOrderPaymentStatus.valueOf(entity.getStatus().name()),
                      entity.getProviderReference(),
                      entity.getPaidAt(),
                      entity.getFailedAt(),
                      entity.getFailureReason(),
                      refund
                  )
                : null;
        var activation =
            purpose == PaymentPurpose.ACTIVATION
                ? new ActivationFeePayment(
                      new ActivationFeePaymentId(entity.getId()),
                      entity.getEventId(),
                      entity.getOrganizerId(),
                      Money.kzt(entity.getAmount()),
                      ActivationFeePaymentStatus.valueOf(entity.getStatus().name()),
                      entity.getProviderReference(),
                      entity.getPaidAt(),
                      entity.getFailedAt(),
                      entity.getFailureReason()
                  )
                : null;
        return new PaymentWork(
            entity.getId(),
            purpose,
            entity.getEventId(),
            entity.getOrderId(),
            entity.getPayerId(),
            entity.getOrganizerId(),
            entity.getAmount(),
            entity.getFee(),
            entity.getInvoiceId(),
            entity.getCallbackSecret(),
            entity.getDeadline(),
            entity.isAbandoned(),
            entity.isReviewRequired(),
            entity.getOperation(),
            entity.getOperationId(),
            entity.isOperationAcknowledged(),
            entity.getBankTransactionId(),
            entity.getOperationState() == null ? null : entity.getOperationState(),
            entity.getOperationAt(),
            ticket,
            activation
        );
    }

    public PaymentWorkJpaEntity toNewEntity(PaymentWork work) {
        return new PaymentWorkJpaEntity(
            work.getId(),
            work.getOrderId(),
            work.getEventId(),
            work.getOrganizerId(),
            work.getPayerId(),
            work.getPurpose(),
            work.getInvoiceId(),
            work.getAmount(),
            work.getFee(),
            work.getCallbackSecret(),
            work.getDeadline(),
            work.isAbandoned(),
            work.isReviewRequired(),
            work.getOperation(),
            work.getOperationId(),
            work.getOperationState() == null ? null : work.getOperationState(),
            work.getOperationAt(),
            work.isOperationAcknowledged(),
            work.getBankTransactionId(),
            work.getStatus(),
            work.isTicket() ? work.getPayment().getProviderReference() : work.getActivationPayment().getProviderReference(),
            work.isTicket() ? work.getPayment().getPaidAt() : work.getActivationPayment().getPaidAt(),
            work.isTicket() ? work.getPayment().getFailedAt() : work.getActivationPayment().getFailedAt(),
            work.isTicket() ? work.getPayment().getFailureReason() : work.getActivationPayment().getFailureReason(),
            work.isTicket()
                ? work
                      .getPayment()
                      .getRefund()
                      .map(refund -> refund.getId().value())
                      .orElse(null)
                : null,
            Instant.EPOCH,
            null
        );
    }

    public void updateEntity(PaymentWork work, PaymentWorkJpaEntity entity) {
        if (!entity.getId().equals(work.getId())) throw new IllegalArgumentException("Cannot map onto another payment id");
        entity.updateMutableState(
            work.isAbandoned(),
            work.isReviewRequired(),
            work.getOperation(),
            work.getOperationId(),
            work.getOperationState() == null ? null : work.getOperationState(),
            work.getOperationAt(),
            work.isOperationAcknowledged(),
            work.getBankTransactionId(),
            work.getStatus(),
            work.isTicket() ? work.getPayment().getProviderReference() : work.getActivationPayment().getProviderReference(),
            work.isTicket() ? work.getPayment().getPaidAt() : work.getActivationPayment().getPaidAt(),
            work.isTicket() ? work.getPayment().getFailedAt() : work.getActivationPayment().getFailedAt(),
            work.isTicket() ? work.getPayment().getFailureReason() : work.getActivationPayment().getFailureReason(),
            work.isTicket()
                ? work
                      .getPayment()
                      .getRefund()
                      .map(refund -> refund.getId().value())
                      .orElse(null)
                : null
        );
    }

    public RefundJpaEntity toNewEntity(Refund refund, UUID paymentId) {
        return new RefundJpaEntity(
            refund.getId().value(),
            paymentId,
            refund.getReason(),
            refund.getRequestedAt(),
            refund.getStatus(),
            refund.getProviderReference(),
            refund.getFailureReason(),
            refund.getCompletedAt()
        );
    }

    public void updateEntity(Refund refund, UUID paymentId, RefundJpaEntity entity) {
        if (!entity.getId().equals(refund.getId().value()) || !entity.getPaymentId().equals(paymentId)) throw new IllegalArgumentException(
            "Cannot map onto another refund identity"
        );
        entity.updateMutableState(refund.getStatus(), refund.getProviderReference(), refund.getFailureReason(), refund.getCompletedAt());
    }
}
