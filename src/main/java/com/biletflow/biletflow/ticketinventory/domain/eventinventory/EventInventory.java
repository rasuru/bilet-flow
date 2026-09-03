package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

import com.biletflow.biletflow.ticketinventory.domain.common.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHold;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservation;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservationId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class EventInventory {

    private final EventInventoryId id;
    private final UUID eventId;
    private final HoldExpiry holdExpiry;
    private final InventoryMode mode;

    public EventInventory(EventInventoryId id, UUID eventId, HoldExpiry holdExpiry, InventoryMode mode) {
        this.id = Objects.requireNonNull(id, "EventInventoryId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.holdExpiry = Objects.requireNonNull(holdExpiry, "holdExpiry cannot be null");
        this.mode = Objects.requireNonNull(mode, "InventoryMode cannot be null");
    }

    public static EventInventory create(UUID eventId, HoldExpiry holdExpiry, InventoryMode mode) {
        return new EventInventory(EventInventoryId.generate(), eventId, holdExpiry, mode);
    }

    // --- GA Operations ---

    public InventoryReservation reserveGA(TicketTypeId ticketTypeId, int quantity, SessionId sessionId, Instant now) {
        return requireGA().reserve(ticketTypeId, quantity, sessionId, holdExpiry, now);
    }

    public void confirmGAReservation(InventoryReservationId reservationId, OrderId orderId, Instant now) {
        requireGA().confirmReservation(reservationId, orderId, now);
    }

    public void releaseGAReservation(InventoryReservationId reservationId, SessionId sessionId) {
        requireGA().releaseReservation(reservationId, sessionId);
    }

    public void configureTicketType(TicketTypeId ticketTypeId, int totalCapacity) {
        requireGA().configureTicketType(ticketTypeId, totalCapacity);
    }

    public void changeTicketTypeCapacity(TicketTypeId ticketTypeId, int newCapacity) {
        requireGA().changeTicketTypeCapacity(ticketTypeId, newCapacity);
    }

    public void reverseGASale(TicketTypeId ticketTypeId, int quantity) {
        requireGA().reverseSale(ticketTypeId, quantity);
    }

    // --- Assigned Seating Operations ---

    public SeatHold holdSeat(TicketTypeId ticketTypeId, SeatId seatId, SessionId sessionId, Instant now) {
        return requireAssigned().holdSeat(ticketTypeId, seatId, sessionId, holdExpiry, now);
    }

    public void confirmSeatHold(SeatHoldId holdId, OrderId orderId, Instant now) {
        requireAssigned().confirmHold(holdId, orderId, now);
    }

    public void releaseSeatHold(SeatHoldId holdId, SessionId sessionId) {
        requireAssigned().releaseHold(holdId, sessionId);
    }

    public void cancelSoldSeat(SeatId seatId, OrderId orderId) {
        requireAssigned().cancelSale(seatId, orderId);
    }

    public void refundSoldSeat(SeatId seatId, OrderId orderId) {
        requireAssigned().refundSale(seatId, orderId);
    }

    // --- Maintenance ---

    public void cleanUpExpiredHolds(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (mode instanceof GeneralAdmissionInventory ga) {
            ga.releaseExpiredReservations(now);
        } else if (mode instanceof AssignedSeatingInventory assigned) {
            assigned.releaseExpiredHolds(now);
        }
    }

    private GeneralAdmissionInventory requireGA() {
        if (!(mode instanceof GeneralAdmissionInventory ga)) {
            throw new IllegalStateException("Operation requires general-admission inventory");
        }
        return ga;
    }

    private AssignedSeatingInventory requireAssigned() {
        if (!(mode instanceof AssignedSeatingInventory assigned)) {
            throw new IllegalStateException("Operation requires assigned-seating inventory");
        }
        return assigned;
    }

    public EventInventoryId getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public HoldExpiry getHoldExpiry() {
        return holdExpiry;
    }

    public InventoryMode getMode() {
        return mode;
    }
}
