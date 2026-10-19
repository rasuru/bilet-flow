package com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperation;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperationState;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentPurpose;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentStatus;
import com.biletflow.biletflow.paymentsmonetization.persistence.payment.PaymentOperationPersistence;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_payment_work")
public class PaymentWorkJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "order_id", unique = true)
    private UUID orderId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "organizer_id", nullable = false)
    private long organizerId;

    @Column(name = "payer_id", nullable = false)
    private long payerId;

    @Column(name = "purpose", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentPurpose purpose;

    @Column(name = "invoice_id", nullable = false, unique = true, length = 15)
    private String invoiceId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    @Column(name = "callback_secret", nullable = false, length = 200)
    private String callbackSecret;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Column(name = "abandoned", nullable = false)
    private boolean abandoned;

    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired;

    @Column(name = "operation", length = 20)
    @Convert(converter = PaymentOperationPersistence.class)
    private PaymentOperation operation;

    @Column(name = "operation_id")
    private UUID operationId;

    @Column(name = "operation_state", length = 30)
    @Enumerated(EnumType.STRING)
    private PaymentOperationState operationState;

    @Column(name = "operation_at")
    private Instant operationAt;

    @Column(name = "operation_acknowledged", nullable = false)
    private boolean operationAcknowledged;

    @Column(name = "bank_transaction_id", length = 100)
    private String bankTransactionId;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "current_refund_id")
    private UUID currentRefundId;

    @Column(name = "next_poll", nullable = false, updatable = false)
    private Instant nextPoll;

    @Column(name = "lease_until", updatable = false)
    private Instant leaseUntil;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PaymentWorkJpaEntity() {}

    public PaymentWorkJpaEntity(
        UUID id,
        UUID orderId,
        UUID eventId,
        long organizerId,
        long payerId,
        PaymentPurpose purpose,
        String invoiceId,
        BigDecimal amount,
        BigDecimal fee,
        String callbackSecret,
        Instant deadline,
        boolean abandoned,
        boolean reviewRequired,
        PaymentOperation operation,
        UUID operationId,
        PaymentOperationState operationState,
        Instant operationAt,
        boolean operationAcknowledged,
        String bankTransactionId,
        PaymentStatus status,
        String providerReference,
        Instant paidAt,
        Instant failedAt,
        String failureReason,
        UUID currentRefundId,
        Instant nextPoll,
        Instant leaseUntil
    ) {
        this.id = id;
        this.orderId = orderId;
        this.eventId = eventId;
        this.organizerId = organizerId;
        this.payerId = payerId;
        this.purpose = purpose;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.fee = fee;
        this.callbackSecret = callbackSecret;
        this.deadline = deadline;
        this.abandoned = abandoned;
        this.reviewRequired = reviewRequired;
        this.operation = operation;
        this.operationId = operationId;
        this.operationState = operationState;
        this.operationAt = operationAt;
        this.operationAcknowledged = operationAcknowledged;
        this.bankTransactionId = bankTransactionId;
        this.status = status;
        this.providerReference = providerReference;
        this.paidAt = paidAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.currentRefundId = currentRefundId;
        this.nextPoll = nextPoll;
        this.leaseUntil = leaseUntil;
    }

    public void updateMutableState(
        boolean abandoned,
        boolean reviewRequired,
        PaymentOperation operation,
        UUID operationId,
        PaymentOperationState operationState,
        Instant operationAt,
        boolean operationAcknowledged,
        String bankTransactionId,
        PaymentStatus status,
        String providerReference,
        Instant paidAt,
        Instant failedAt,
        String failureReason,
        UUID currentRefundId
    ) {
        this.abandoned = abandoned;
        this.reviewRequired = reviewRequired;
        this.operation = operation;
        this.operationId = operationId;
        this.operationState = operationState;
        this.operationAt = operationAt;
        this.operationAcknowledged = operationAcknowledged;
        this.bankTransactionId = bankTransactionId;
        this.status = status;
        this.providerReference = providerReference;
        this.paidAt = paidAt;
        this.failedAt = failedAt;
        this.failureReason = failureReason;
        this.currentRefundId = currentRefundId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getOrganizerId() {
        return organizerId;
    }

    public long getPayerId() {
        return payerId;
    }

    public PaymentPurpose getPurpose() {
        return purpose;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFee() {
        return fee;
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

    public PaymentOperationState getOperationState() {
        return operationState;
    }

    public Instant getOperationAt() {
        return operationAt;
    }

    public boolean isOperationAcknowledged() {
        return operationAcknowledged;
    }

    public String getBankTransactionId() {
        return bankTransactionId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public UUID getCurrentRefundId() {
        return currentRefundId;
    }

    public Instant getNextPoll() {
        return nextPoll;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public long getVersion() {
        return version;
    }
}
