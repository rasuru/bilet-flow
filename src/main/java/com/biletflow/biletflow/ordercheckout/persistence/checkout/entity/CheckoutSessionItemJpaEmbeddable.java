package com.biletflow.biletflow.ordercheckout.persistence.checkoutsession.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class CheckoutSessionItemJpaEmbeddable {
    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;


    @Column(name = "seat_id")
    private UUID seatId;

    protected CheckoutSessionItemJpaEmbeddable() {}

    public CheckoutSessionItemJpaEmbeddable(UUID ticketTypeId, BigDecimal price, String priceDecimal, UUID seatId) {
        this.ticketTypeId = ticketTypeId;
        this.price = price;
        this.priceCurrency = priceCurrency;
        this.seatId = seatId;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public UUID getSeatId() {
        return seatId;
    }
}
