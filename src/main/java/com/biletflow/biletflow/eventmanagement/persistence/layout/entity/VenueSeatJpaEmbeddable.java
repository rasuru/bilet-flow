package com.biletflow.biletflow.eventmanagement.persistence.layout.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Embeddable
public class VenueSeatJpaEmbeddable {

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(name = "section_label", nullable = false, length = 100)
    private String section;

    @Column(name = "row_label", nullable = false, length = 100)
    private String row;

    @Column(name = "seat_number", nullable = false, length = 100)
    private String seatNumber;

    @Column(name = "accessible", nullable = false)
    private boolean accessible;

    @Column(name = "price_category", length = 100)
    private String priceCategory;

    protected VenueSeatJpaEmbeddable() {}

    public VenueSeatJpaEmbeddable(UUID seatId, String section, String row, String seatNumber, boolean accessible, String priceCategory) {
        this.seatId = seatId;
        this.section = section;
        this.row = row;
        this.seatNumber = seatNumber;
        this.accessible = accessible;
        this.priceCategory = priceCategory;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public String getSection() {
        return section;
    }

    public String getRow() {
        return row;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public boolean isAccessible() {
        return accessible;
    }

    public String getPriceCategory() {
        return priceCategory;
    }
}
