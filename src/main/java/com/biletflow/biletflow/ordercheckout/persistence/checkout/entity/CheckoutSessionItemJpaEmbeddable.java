package com.biletflow.biletflow.ordercheckout.persistence.checkoutsession.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class CheckoutSessionItemJpaEmbeddable {
    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "seat_id")
    private UUID seatId;

    protected CheckoutSessionItemJpaEmbeddable() {}

    public CheckoutSessionItemJpaEmbeddable(UUID ticketTypeId, double price, UUID seatId) {
        this.ticketTypeId = ticketTypeId;
        this.price = price;
        this.seatId = seatId;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public double getPrice() {
        return price;
    }

    public UUID getSeatId() {
        return seatId;
    }
}
