package com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga;

import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Instant;
import java.util.Objects;

public class InventoryReservation {

    private final InventoryReservationId id;
    private final TicketTypeId ticketTypeId;
    private final int quantity;
    private final SessionId sessionId;
    private OrderId orderId;
    private ReservationStatus status;
    private final Instant expiresAt;

    public InventoryReservation(
        InventoryReservationId id,
        TicketTypeId ticketTypeId,
        int quantity,
        SessionId sessionId,
        OrderId orderId,
        ReservationStatus status,
        Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id, "InventoryReservationId cannot be null");
        this.ticketTypeId = Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be greater than 0");
        }
        this.quantity = quantity;
        this.sessionId = Objects.requireNonNull(sessionId, "SessionId cannot be null");
        this.orderId = orderId;
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    public static InventoryReservation create(TicketTypeId ticketTypeId, int quantity, SessionId sessionId, Instant expiresAt) {
        return new InventoryReservation(
            InventoryReservationId.generate(),
            ticketTypeId,
            quantity,
            sessionId,
            null,
            ReservationStatus.RESERVED,
            expiresAt
        );
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        return status == ReservationStatus.RESERVED && !now.isBefore(expiresAt);
    }

    /**
     * @return true only when this call performs RESERVED -> CONFIRMED.
     *         A same-order retry is accepted and returns false.
     */
    public boolean confirm(OrderId orderId, Instant now) {
        Objects.requireNonNull(orderId, "OrderId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        if (status == ReservationStatus.CONFIRMED) {
            if (orderId.equals(this.orderId)) {
                return false;
            }
            throw new IllegalStateException("Reservation is already confirmed for another order");
        }

        ensureReserved();

        if (isExpiredAt(now)) {
            throw new IllegalStateException("Reservation has expired");
        }

        if (this.orderId != null && !this.orderId.equals(orderId)) {
            throw new IllegalStateException("Reservation already belongs to another order");
        }

        this.orderId = orderId;
        this.status = ReservationStatus.CONFIRMED;
        return true;
    }

    public void release(SessionId sessionId) {
        ensureOwnedBy(sessionId);
        this.status = ReservationStatus.RELEASED;
    }

    public void markExpired() {
        ensureReserved();
        this.status = ReservationStatus.EXPIRED;
    }

    private void ensureOwnedBy(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "SessionId cannot be null");

        if (!this.sessionId.equals(sessionId)) {
            throw new IllegalStateException("Reservation belongs to another checkout session");
        }

        ensureReserved();
    }

    private void ensureReserved() {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Reservation is not active: " + status);
        }
    }

    public InventoryReservationId getId() {
        return id;
    }

    public TicketTypeId getTicketTypeId() {
        return ticketTypeId;
    }

    public int getQuantity() {
        return quantity;
    }

    public SessionId getSessionId() {
        return sessionId;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
