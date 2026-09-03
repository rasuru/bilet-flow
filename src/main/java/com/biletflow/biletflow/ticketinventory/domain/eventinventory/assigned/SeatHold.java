package com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned;

import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Instant;
import java.util.Objects;

public class SeatHold {

    private final SeatHoldId id;
    private final TicketTypeId ticketTypeId;
    private final SeatId seatId;
    private final SessionId sessionId;
    private OrderId orderId;
    private SeatHoldStatus status;
    private final Instant expiresAt;

    public SeatHold(
        SeatHoldId id,
        TicketTypeId ticketTypeId,
        SeatId seatId,
        SessionId sessionId,
        OrderId orderId,
        SeatHoldStatus status,
        Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id, "SeatHoldId cannot be null");
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
        this.seatId = Objects.requireNonNull(seatId, "SeatId cannot be null");
        this.sessionId = Objects.requireNonNull(sessionId, "SessionId cannot be null");
        this.orderId = orderId;
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    public static SeatHold create(TicketTypeId ticketTypeId, SeatId seatId, SessionId sessionId, Instant expiresAt) {
        return new SeatHold(SeatHoldId.generate(), ticketTypeId, seatId, sessionId, null, SeatHoldStatus.HELD, expiresAt);
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        return status == SeatHoldStatus.HELD && !now.isBefore(expiresAt);
    }

    /**
     * @return true only when this call performs HELD -> SOLD.
     *         A same-order retry is accepted and returns false.
     */
    public boolean confirm(OrderId orderId, Instant now) {
        Objects.requireNonNull(orderId, "OrderId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        if (status == SeatHoldStatus.SOLD) {
            if (orderId.equals(this.orderId)) {
                return false;
            }
            throw new IllegalStateException("Seat is already sold to another order");
        }

        ensureHeld();

        if (isExpiredAt(now)) {
            throw new IllegalStateException("Seat hold has expired");
        }

        if (this.orderId != null && !this.orderId.equals(orderId)) {
            throw new IllegalStateException("Seat hold already belongs to another order");
        }

        this.orderId = orderId;
        this.status = SeatHoldStatus.SOLD;
        return true;
    }

    public void release(SessionId sessionId) {
        ensureOwnedBy(sessionId);
        this.status = SeatHoldStatus.RELEASED;
    }

    public void markExpired() {
        ensureHeld();
        this.status = SeatHoldStatus.EXPIRED;
    }

    public void markCancelled(OrderId orderId) {
        ensureSoldTo(orderId);
        this.status = SeatHoldStatus.CANCELLED;
    }

    public void markRefunded(OrderId orderId) {
        ensureSoldTo(orderId);
        this.status = SeatHoldStatus.REFUNDED;
    }

    private void ensureOwnedBy(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "SessionId cannot be null");

        if (!this.sessionId.equals(sessionId)) {
            throw new IllegalStateException("Seat hold belongs to another checkout session");
        }

        ensureHeld();
    }

    private void ensureSoldTo(OrderId orderId) {
        Objects.requireNonNull(orderId, "OrderId cannot be null");

        if (status != SeatHoldStatus.SOLD) {
            throw new IllegalStateException("Seat allocation is not currently sold: " + status);
        }

        if (!orderId.equals(this.orderId)) {
            throw new IllegalStateException("Seat allocation belongs to another order");
        }
    }

    private void ensureHeld() {
        if (status != SeatHoldStatus.HELD) {
            throw new IllegalStateException("Seat hold is not active: " + status);
        }
    }

    public SeatHoldId getId() {
        return id;
    }

    public TicketTypeId getTicketTypeId() {
        return ticketTypeId;
    }

    public SeatId getSeatId() {
        return seatId;
    }

    public SessionId getSessionId() {
        return sessionId;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public SeatHoldStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
