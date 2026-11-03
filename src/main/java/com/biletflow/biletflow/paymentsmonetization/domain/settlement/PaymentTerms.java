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

    public PaymentTerms withActivationFee(BigDecimal fee) {
        return new PaymentTerms(fee, processingFeeRate);
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
        var payment = TicketOrderPayment.initiate(order, event, organizer, Money.kzt(amount));
        return new PaymentWork(
            payment.getId().value(),
            PaymentPurpose.TICKET,
            event,
            order,
            payer,
            organizer,
            amount.setScale(2, RoundingMode.UNNECESSARY),
            feeFor(amount),
            invoice,
            secret,
            deadline,
            false,
            false,
            null,
            null,
            false,
            null,
            null,
            null,
            payment,
            null
        );
    }

    public PaymentWork activation(UUID event, long organizer, Instant now, String invoice, String secret) {
        var payment = ActivationFeePayment.initiate(event, organizer, Money.kzt(activationFee));
        return new PaymentWork(
            payment.getId().value(),
            PaymentPurpose.ACTIVATION,
            event,
            null,
            organizer,
            organizer,
            activationFee.setScale(2, RoundingMode.UNNECESSARY),
            BigDecimal.ZERO,
            invoice,
            secret,
            now.plusSeconds(1800),
            false,
            false,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            payment
        );
    }

    private BigDecimal feeFor(BigDecimal amount) {
        return amount.multiply(processingFeeRate).setScale(2, RoundingMode.HALF_UP);
    }
}
