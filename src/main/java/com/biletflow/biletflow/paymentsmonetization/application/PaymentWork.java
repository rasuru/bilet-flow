package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePayment;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.TicketOrderPayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persisted process state. Financial transitions belong to the contained domain aggregate. */
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
}
