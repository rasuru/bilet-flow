package com.biletflow.biletflow.paymentsmonetization.domain.settlement;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePayment;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.TicketOrderPayment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** Immutable business pricing and payment creation rules, independent of provider configuration. */
public record PaymentTerms(BigDecimal activationFee, BigDecimal processingFeeRate) {
    public PaymentTerms {
        if (
            activationFee == null ||
            activationFee.signum() <= 0 ||
            activationFee.scale() > 2 ||
            processingFeeRate == null ||
            processingFeeRate.signum() < 0 ||
            processingFeeRate.compareTo(BigDecimal.ONE) >= 0
        ) throw new IllegalArgumentException("Invalid payment terms");
    }

    public void requireTicketRequest(long payer, BigDecimal amount, Instant deadline, Instant now) {
        if (payer <= 0 || amount == null || amount.signum() <= 0 || amount.scale() > 2) throw new IllegalArgumentException(
            "Invalid ticket payment request"
        );
        if (deadline == null || !deadline.isAfter(now)) throw new IllegalStateException("Checkout expired");
    }

    public void requirePaidSales(boolean eventOpen, boolean activated) {
        if (!eventOpen) throw new IllegalStateException("Event is not accepting purchases");
        if (!activated) throw new IllegalStateException("Paid sales are not active");
    }

    public void requireActivationRequest(
        boolean acceptedTerms,
        boolean simulatedVerification,
        boolean cancelled,
        Instant startsAt,
        Instant now
    ) {
        if (!acceptedTerms || !simulatedVerification) throw new IllegalArgumentException(
            "Accept demonstration paid-event terms and simulated verification"
        );
        if (cancelled || !startsAt.isAfter(now)) throw new IllegalStateException("Event cannot activate paid sales");
    }

    public PaymentWork ticket(
        UUID order,
        UUID event,
        long payer,
        long organizer,
        BigDecimal amount,
        Instant deadline,
        Instant now,
        String invoice,
        String secret
    ) {
        requireTicketRequest(payer, amount, deadline, now);
        var work = base("TICKET", event, payer, organizer, amount, deadline, invoice, secret);
        work.orderId = order;
        work.payment = TicketOrderPayment.initiate(order, event, organizer, Money.kzt(amount));
        work.id = work.payment.getId().value();
        return work;
    }

    public PaymentWork activation(UUID event, long organizer, Instant now, String invoice, String secret) {
        var work = base("ACTIVATION", event, organizer, organizer, activationFee, now.plusSeconds(1800), invoice, secret);
        work.fee = BigDecimal.ZERO;
        work.activationPayment = ActivationFeePayment.initiate(event, organizer, Money.kzt(activationFee));
        work.id = work.activationPayment.getId().value();
        return work;
    }

    private PaymentWork base(
        String purpose,
        UUID event,
        long payer,
        long organizer,
        BigDecimal amount,
        Instant deadline,
        String invoice,
        String secret
    ) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) throw new IllegalArgumentException("Invalid payment amount");
        var work = new PaymentWork();
        work.purpose = purpose;
        work.eventId = event;
        work.payerId = payer;
        work.organizerId = organizer;
        work.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        work.fee = work.amount.multiply(processingFeeRate).setScale(2, RoundingMode.HALF_UP);
        work.deadline = deadline;
        work.invoiceId = invoice;
        work.callbackSecret = secret;
        return work;
    }
}
