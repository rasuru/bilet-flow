package com.biletflow.biletflow.ticketinventory.domain.ticket;

import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Ticket {

    private final TicketId id;
    private final TicketTypeId ticketTypeId;
    private final UUID eventId;
    private final OrderId orderId;
    private final AttendeeEmail attendeeEmail;
    private final SeatId seatId; // null for GA
    private final TicketCode ticketCode;
    private final Instant issuedAt;

    private Long ownerUserId;
    private TicketStatus status;

    public Ticket(
        TicketId id,
        TicketTypeId ticketTypeId,
        UUID eventId,
        OrderId orderId,
        AttendeeEmail attendeeEmail,
        Long ownerUserId,
        SeatId seatId,
        TicketCode ticketCode,
        TicketStatus status,
        Instant issuedAt
    ) {
        this.id = Objects.requireNonNull(id, "TicketId cannot be null");
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "OrderId cannot be null");
        this.attendeeEmail = Objects.requireNonNull(attendeeEmail, "AttendeeEmail cannot be null");
        this.ownerUserId = ownerUserId;
        this.seatId = seatId;
        this.ticketCode = Objects.requireNonNull(ticketCode, "TicketCode cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt cannot be null");
    }

    public static Ticket issue(
        TicketTypeId ticketTypeId,
        UUID eventId,
        OrderId orderId,
        AttendeeEmail attendeeEmail,
        Long ownerUserId,
        SeatId seatId,
        Instant now
    ) {
        return new Ticket(
            TicketId.generate(),
            ticketTypeId,
            eventId,
            orderId,
            attendeeEmail,
            ownerUserId,
            seatId,
            TicketCode.generate(),
            TicketStatus.VALID,
            now
        );
    }

    /**
     * Application layer may call this only after Identity confirms that the
     * verified user's email matches attendeeEmail.
     *
     * Same-user retries are idempotent; reassignment to another account fails.
     */
    public void linkOwner(Long userId) {
        Objects.requireNonNull(userId, "UserId cannot be null");

        if (ownerUserId == null) {
            ownerUserId = userId;
            return;
        }

        if (!ownerUserId.equals(userId)) {
            throw new IllegalStateException("Ticket is already linked to another user");
        }
    }

    public void checkIn() {
        if (status != TicketStatus.VALID) {
            throw new IllegalStateException("Only VALID tickets can be checked in; current status: " + status);
        }

        status = TicketStatus.CHECKED_IN;
    }

    public void reverseCheckIn() {
        if (status != TicketStatus.CHECKED_IN) {
            throw new IllegalStateException("Only CHECKED_IN tickets can have check-in reversed");
        }

        status = TicketStatus.VALID;
    }

    /**
     * @return true only when VALID -> CANCELLED occurs.
     *         Same-state retries return false.
     */
    public boolean cancel() {
        if (status == TicketStatus.CANCELLED) {
            return false;
        }

        if (status != TicketStatus.VALID) {
            throw new IllegalStateException("Cannot cancel ticket with status: " + status);
        }

        status = TicketStatus.CANCELLED;
        return true;
    }

    /**
     * @return true only when VALID -> REFUNDED occurs.
     *         Same-state retries return false.
     */
    public boolean markRefunded() {
        if (status == TicketStatus.REFUNDED) {
            return false;
        }

        if (status != TicketStatus.VALID) {
            throw new IllegalStateException("Cannot refund ticket with status: " + status);
        }

        status = TicketStatus.REFUNDED;
        return true;
    }

    public TicketId getId() {
        return id;
    }

    public TicketTypeId getTicketTypeId() {
        return ticketTypeId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public AttendeeEmail getAttendeeEmail() {
        return attendeeEmail;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public SeatId getSeatId() {
        return seatId;
    }

    public TicketCode getTicketCode() {
        return ticketCode;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
