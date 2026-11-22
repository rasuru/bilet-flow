package com.biletflow.biletflow.promotions.domain.campaign;

import com.biletflow.biletflow.common.domain.Money;
import java.util.Objects;

public record DiscountQuote(Money eligibleSubtotal, int eligibleTicketQuantity, Money discountAmount) {
    public DiscountQuote {
        Objects.requireNonNull(eligibleSubtotal, "eligibleSubtotal cannot be null");
        Objects.requireNonNull(discountAmount, "discountAmount cannot be null");

        if (eligibleTicketQuantity <= 0) {
            throw new IllegalArgumentException("Eligible ticket quantity must be greater than 0");
        }

        if (discountAmount.isGreaterThan(eligibleSubtotal)) {
            throw new IllegalArgumentException("Discount cannot exceed the eligible subtotal");
        }
    }
}
