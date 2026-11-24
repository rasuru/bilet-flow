package com.biletflow.biletflow.ticketinventory.persistence.ticket.entity;

import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;

@Entity
@Table(
    name = "ticket_inventory_ticket",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_inventory_ticket_code", columnNames = "ticket_code"),
        @UniqueConstraint(name = "uk_ticket_inventory_ticket_manual_code", columnNames = { "event_id", "manual_code" }),
    },
    indexes = {
        @Index(name = "idx_ticket_inventory_ticket_order", columnList = "order_id"),
        @Index(name = "idx_ticket_inventory_ticket_event", columnList = "event_id"),
        @Index(name = "idx_ticket_inventory_ticket_owner", columnList = "owner_user_id"),
    }
)
public class TicketJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ticket_type_id", nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "attendee_email", nullable = false, updatable = false, length = 320)
    private String attendeeEmail;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "seat_id", updatable = false)
    private UUID seatId;

    @Column(name = "ticket_code", nullable = false, updatable = false)
    private UUID ticketCode;

    @Generated
    @Column(name = "manual_code", nullable = false, insertable = false, updatable = false, length = 8)
    private String manualCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected TicketJpaEntity() {}

    public TicketJpaEntity(
        UUID id,
        UUID ticketTypeId,
        UUID eventId,
        UUID orderId,
        String attendeeEmail,
        Long ownerUserId,
        UUID seatId,
        UUID ticketCode,
        TicketStatus status,
        Instant issuedAt
    ) {
        this.id = id;
        this.ticketTypeId = ticketTypeId;
        this.eventId = eventId;
        this.orderId = orderId;
        this.attendeeEmail = attendeeEmail;
        this.ownerUserId = ownerUserId;
        this.seatId = seatId;
        this.ticketCode = ticketCode;
        this.status = status;
        this.issuedAt = issuedAt;
    }

    public void updateMutableState(Long ownerUserId, TicketStatus status) {
        this.ownerUserId = ownerUserId;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getAttendeeEmail() {
        return attendeeEmail;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public UUID getTicketCode() {
        return ticketCode;
    }

    public String getManualCode() {
        return manualCode;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
