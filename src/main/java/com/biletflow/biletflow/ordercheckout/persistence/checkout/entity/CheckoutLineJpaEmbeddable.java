package com.biletflow.biletflow.ordercheckout.persistence.checkout.entity;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs.Line;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Embeddable
public class CheckoutLineJpaEmbeddable {

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "seat_id")
    private UUID seatId;

    @Column(name = "price_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "price_currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "attendee_email", length = 320, nullable = false)
    private String email;

    protected CheckoutLineJpaEmbeddable() {}

    public CheckoutLineJpaEmbeddable(Line line) {
        ticketTypeId = line.ticketTypeId();
        seatId = line.seatId();
        amount = line.price().amount();
        currency = line.price().currency().getCurrencyCode();
        email = line.email();
    }

    public Line toDomain() {
        return new Line(ticketTypeId, seatId, Money.of(amount, Currency.getInstance(currency)), email);
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

    public String getEmail() {
        return email;
    }
}
