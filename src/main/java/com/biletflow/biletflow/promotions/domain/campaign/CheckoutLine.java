package com.biletflow.biletflow.promotions.domain.campaign;

import com.biletflow.biletflow.common.domain.Money;
import java.util.Objects;
import java.util.UUID;

public record CheckoutLine(UUID ticketTypeId, Money unitPrice, int quantity) {
    public CheckoutLine {
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");
        Objects.requireNonNull(unitPrice, "unitPrice cannot be null");

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
    }

    public Money total() {
        return unitPrice.multiply(quantity);
    }
}
