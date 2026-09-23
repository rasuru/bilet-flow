package com.biletflow.biletflow.ordercheckout.persistence.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class OrderItemJpaEmbeddable {

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "seat_id")
    private UUID seatId;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "used", nullable = false)
    private boolean used;

    protected OrderItemJpaEmbeddable() {
    }

    public OrderItemJpaEmbeddable(
        UUID ticketTypeId,
        UUID seatId,
        double price,
        boolean used
    ) {
        this.ticketTypeId = ticketTypeId;
        this.seatId = seatId;
        this.price = price;
        this.used = used;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public double getPrice() {
        return price;
    }

    public boolean isUsed() {
        return used;
    }
}
