package com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment;

import java.util.Objects;
import java.util.UUID;

public record TicketOrderPaymentId(UUID value) {
    public TicketOrderPaymentId {
        Objects.requireNonNull(value, "PaymentId cannot be null");
    }

    public static TicketOrderPaymentId generate() {
        return new TicketOrderPaymentId(UUID.randomUUID());
    }
}
