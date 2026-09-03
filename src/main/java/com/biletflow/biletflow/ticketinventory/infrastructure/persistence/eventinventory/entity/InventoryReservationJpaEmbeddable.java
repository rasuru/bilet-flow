package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.entity;

import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.ReservationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Embeddable
public class InventoryReservationJpaEmbeddable {

    @Column(name = "reservation_id", nullable = false)
    private UUID id;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected InventoryReservationJpaEmbeddable() {}

    public InventoryReservationJpaEmbeddable(
        UUID id,
        UUID ticketTypeId,
        int quantity,
        String sessionId,
        UUID orderId,
        ReservationStatus status,
        Instant expiresAt
    ) {
        this.id = id;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
        this.sessionId = sessionId;
        this.orderId = orderId;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
