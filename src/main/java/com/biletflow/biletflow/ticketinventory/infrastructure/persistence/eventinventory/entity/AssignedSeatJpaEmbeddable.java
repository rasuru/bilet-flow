package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class AssignedSeatJpaEmbeddable {

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(name = "price_category", length = 100)
    private String priceCategory;

    protected AssignedSeatJpaEmbeddable() {}

    public AssignedSeatJpaEmbeddable(UUID seatId, String priceCategory) {
        this.seatId = seatId;
        this.priceCategory = priceCategory;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public String getPriceCategory() {
        return priceCategory;
    }
}
