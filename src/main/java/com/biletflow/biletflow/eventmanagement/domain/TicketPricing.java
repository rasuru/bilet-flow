package com.biletflow.biletflow.eventmanagement.domain;

import java.math.BigDecimal;
import java.util.Objects;

public sealed interface TicketPricing permits TicketPricing.Free, TicketPricing.Paid {
    record Free() implements TicketPricing {}

    record Paid(BigDecimal amount, String currency) implements TicketPricing {
        public Paid {
            Objects.requireNonNull(amount, "Amount cannot be null");
            Objects.requireNonNull(currency, "Currency cannot be null");
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Paid ticket amount must be greater than zero");
            }
        }
    }
}
