package com.biletflow.biletflow.ordercheckout.persistence.common.entity;

import com.biletflow.biletflow.common.domain.Money;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Embeddable
public class PurchaseItemJpaEmbeddable {

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "seat_id")
    private UUID seatId;

    @Column(name = "price_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "price_currency", length = 3, nullable = false)
    private String currency;

    protected PurchaseItemJpaEmbeddable() {}

    public PurchaseItemJpaEmbeddable(UUID ticketTypeId, UUID seatId, Money price) {
        this.ticketTypeId = ticketTypeId;
        this.seatId = seatId;
        amount = price.amount();
        currency = price.currency().getCurrencyCode();
    }

    public Money getPrice() {
        return Money.of(amount, Currency.getInstance(currency));
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}
