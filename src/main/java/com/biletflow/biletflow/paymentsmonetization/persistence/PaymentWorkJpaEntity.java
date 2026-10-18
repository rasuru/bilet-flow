package com.biletflow.biletflow.paymentsmonetization.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pm_payment_work")
public class PaymentWorkJpaEntity {

    @Id
    @Column(name = "id")
    UUID id;

    @Column(name = "order_id", unique = true)
    UUID orderId;

    @Column(name = "event_id", nullable = false)
    UUID eventId;

    @Column(name = "organizer_id", nullable = false)
    long organizerId;

    @Column(name = "payer_id", nullable = false)
    long payerId;

    @Column(name = "purpose", nullable = false, length = 20)
    String purpose;

    @Column(name = "invoice_id", nullable = false, unique = true, length = 15)
    String invoiceId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    BigDecimal amount;

    @Column(name = "fee", nullable = false, precision = 19, scale = 2)
    BigDecimal fee;

    @Column(name = "callback_secret", nullable = false, length = 200)
    String callbackSecret;

    @Column(name = "deadline", nullable = false)
    Instant deadline;

    @Column(name = "abandoned", nullable = false)
    boolean abandoned;

    @Column(name = "review_required", nullable = false)
    boolean reviewRequired;

    @Column(name = "operation", length = 20)
    String operation;

    @Column(name = "operation_id")
    UUID operationId;

    @Column(name = "operation_state", length = 30)
    String operationState;

    @Column(name = "operation_at")
    Instant operationAt;

    @Column(name = "operation_acknowledged", nullable = false)
    boolean operationAcknowledged;

    @Column(name = "bank_transaction_id", length = 100)
    String bankTransactionId;

    @Column(name = "status", nullable = false, length = 30)
    String status;

    @Column(name = "provider_reference", length = 100)
    String providerReference;

    @Column(name = "paid_at")
    Instant paidAt;

    @Column(name = "failed_at")
    Instant failedAt;

    @Column(name = "failure_reason", length = 200)
    String failureReason;

    @Column(name = "current_refund_id")
    UUID currentRefundId;

    @Column(name = "next_poll", nullable = false, updatable = false)
    Instant nextPoll;

    @Column(name = "lease_until", updatable = false)
    Instant leaseUntil;

    @Version
    @Column(name = "version", nullable = false)
    long version;

    protected PaymentWorkJpaEntity() {}
}
