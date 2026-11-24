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

    @Column(name = "price_category", length = 100)
    private String priceCategory;

    @Column(name = "position_x")
    private Integer positionX;

    @Column(name = "position_y")
    private Integer positionY;

    protected VenueSeatJpaEmbeddable() {}

    public VenueSeatJpaEmbeddable(UUID seatId, String section, String row, String seatNumber, String priceCategory, Integer positionX, Integer positionY) {
        this.positionX = positionX;
        this.positionY = positionY;
        this.seatId = seatId;
        this.section = section;
        this.row = row;
        this.seatNumber = seatNumber;
        this.priceCategory = priceCategory;
    }

    public Integer getPositionX() {
        return positionX;
    }

    public Integer getPositionY() {
        return positionY;
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

    public String getPriceCategory() {
        return priceCategory;
    }
}

