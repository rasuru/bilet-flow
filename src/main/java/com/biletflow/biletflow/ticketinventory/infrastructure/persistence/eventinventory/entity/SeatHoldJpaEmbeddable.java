package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.entity;

import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Embeddable
public class SeatHoldJpaEmbeddable {

    @Column(name = "hold_id", nullable = false)
    private UUID id;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SeatHoldStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SeatHoldJpaEmbeddable() {}

    public SeatHoldJpaEmbeddable(
        UUID id,
        UUID ticketTypeId,
        UUID seatId,
        String sessionId,
        UUID orderId,
        SeatHoldStatus status,
        Instant expiresAt
    ) {
        this.id = id;
        this.ticketTypeId = ticketTypeId;
        this.seatId = seatId;
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

    public UUID getSeatId() {
        return seatId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public SeatHoldStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
