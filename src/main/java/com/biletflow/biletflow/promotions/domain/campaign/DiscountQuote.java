package com.biletflow.biletflow.promotions.domain.campaign;

import com.biletflow.biletflow.common.domain.Money;
import java.util.Objects;

public record DiscountQuote(Money eligibleSubtotal, Money discountAmount) {
    public DiscountQuote {
        Objects.requireNonNull(eligibleSubtotal, "eligibleSubtotal cannot be null");
        Objects.requireNonNull(discountAmount, "discountAmount cannot be null");

        if (discountAmount.isGreaterThan(eligibleSubtotal)) {
            throw new IllegalArgumentException("Discount cannot exceed the eligible subtotal");
        }
    }
}
