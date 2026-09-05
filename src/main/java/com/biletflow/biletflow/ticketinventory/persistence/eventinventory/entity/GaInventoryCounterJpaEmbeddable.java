package com.biletflow.biletflow.ticketinventory.persistence.eventinventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class GaInventoryCounterJpaEmbeddable {

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    @Column(name = "sold", nullable = false)
    private int sold;

    protected GaInventoryCounterJpaEmbeddable() {}

    public GaInventoryCounterJpaEmbeddable(UUID ticketTypeId, int totalCapacity, int reserved, int sold) {
        this.ticketTypeId = ticketTypeId;
        this.totalCapacity = totalCapacity;
        this.reserved = reserved;
        this.sold = sold;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getReserved() {
        return reserved;
    }

    public int getSold() {
        return sold;
    }
}
