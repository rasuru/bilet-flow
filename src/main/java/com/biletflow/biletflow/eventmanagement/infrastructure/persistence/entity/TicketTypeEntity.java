package com.biletflow.biletflow.eventmanagement.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_type")
public class TicketTypeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "pricing_type", nullable = false)
    private String pricingType;

    @Column(name = "price_amount")
    private BigDecimal priceAmount;

    @Column(name = "price_currency")
    private String priceCurrency;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "sales_start", nullable = false)
    private Instant salesStart;

    @Column(name = "sales_end", nullable = false)
    private Instant salesEnd;

    @Column(name = "max_per_order", nullable = false)
    private int maxPerOrder;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    public TicketTypeEntity() {}

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPricingType() {
        return pricingType;
    }

    public void setPricingType(String pricingType) {
        this.pricingType = pricingType;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public void setPriceAmount(BigDecimal priceAmount) {
        this.priceAmount = priceAmount;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public void setPriceCurrency(String priceCurrency) {
        this.priceCurrency = priceCurrency;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Instant getSalesStart() {
        return salesStart;
    }

    public void setSalesStart(Instant salesStart) {
        this.salesStart = salesStart;
    }

    public Instant getSalesEnd() {
        return salesEnd;
    }

    public void setSalesEnd(Instant salesEnd) {
        this.salesEnd = salesEnd;
    }

    public int getMaxPerOrder() {
        return maxPerOrder;
    }

    public void setMaxPerOrder(int maxPerOrder) {
        this.maxPerOrder = maxPerOrder;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
