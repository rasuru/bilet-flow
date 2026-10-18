package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs.Line;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Embeddable
public class CheckoutLineJpaValue {

    @Column(name = "ticket_type_id", nullable = false)
    UUID ticketTypeId;

    @Column(name = "seat_id")
    UUID seatId;

    @Column(name = "price_amount", precision = 19, scale = 2, nullable = false)
    BigDecimal amount;

    @Column(name = "price_currency", length = 3, nullable = false)
    String currency;

    @Column(name = "attendee_email", length = 320, nullable = false)
    String email;

    protected CheckoutLineJpaValue() {}

    CheckoutLineJpaValue(Line line) {
        ticketTypeId = line.ticketTypeId();
        seatId = line.seatId();
        amount = line.price().amount();
        currency = line.price().currency().getCurrencyCode();
        email = line.email();
    }

    Line domain() {
        return new Line(ticketTypeId, seatId, Money.of(amount, Currency.getInstance(currency)), email);
    }
}
