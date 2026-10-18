package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.domain.Money;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Embeddable
public class PurchaseItemJpaValue {

    @Column(name = "ticket_type_id", nullable = false)
    UUID ticketTypeId;

    @Column(name = "seat_id")
    UUID seatId;

    @Column(name = "price_amount", precision = 19, scale = 2, nullable = false)
    BigDecimal amount;

    @Column(name = "price_currency", length = 3, nullable = false)
    String currency;

    protected PurchaseItemJpaValue() {}

    PurchaseItemJpaValue(UUID ticketTypeId, UUID seatId, Money price) {
        this.ticketTypeId = ticketTypeId;
        this.seatId = seatId;
        amount = price.amount();
        currency = price.currency().getCurrencyCode();
    }

    Money price() {
        return Money.of(amount, Currency.getInstance(currency));
    }
}
