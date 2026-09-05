package com.biletflow.biletflow.ticketinventory.persistence.tickettype.entity;

import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_inventory_ticket_type", indexes = @Index(name = "idx_ticket_inventory_ticket_type_event", columnList = "event_id"))
public class TicketTypeJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "price_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    @Column(name = "max_per_order", nullable = false)
    private int maxPerOrder;

    @Column(name = "sales_start_at", nullable = false)
    private Instant salesStartAt;

    @Column(name = "sales_end_at", nullable = false)
    private Instant salesEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TicketTypeStatus status;

    @Column(name = "price_category", length = 100, updatable = false)
    private String priceCategory;

    protected TicketTypeJpaEntity() {}

    public TicketTypeJpaEntity(
        UUID id,
        UUID eventId,
        String name,
        String description,
        BigDecimal priceAmount,
        String priceCurrency,
        int maxPerOrder,
        Instant salesStartAt,
        Instant salesEndAt,
        TicketTypeStatus status,
        String priceCategory
    ) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.description = description;
        this.priceAmount = priceAmount;
        this.priceCurrency = priceCurrency;
        this.maxPerOrder = maxPerOrder;
        this.salesStartAt = salesStartAt;
        this.salesEndAt = salesEndAt;
        this.status = status;
        this.priceCategory = priceCategory;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public int getMaxPerOrder() {
        return maxPerOrder;
    }

    public Instant getSalesStartAt() {
        return salesStartAt;
    }

    public Instant getSalesEndAt() {
        return salesEndAt;
    }

    public TicketTypeStatus getStatus() {
        return status;
    }

    public String getPriceCategory() {
        return priceCategory;
    }

    public void update(
        String name,
        String description,
        BigDecimal priceAmount,
        String priceCurrency,
        int maxPerOrder,
        Instant salesStartAt,
        Instant salesEndAt,
        TicketTypeStatus status
    ) {
        this.name = name;
        this.description = description;
        this.priceAmount = priceAmount;
        this.priceCurrency = priceCurrency;
        this.maxPerOrder = maxPerOrder;
        this.salesStartAt = salesStartAt;
        this.salesEndAt = salesEndAt;
        this.status = status;
    }
}
