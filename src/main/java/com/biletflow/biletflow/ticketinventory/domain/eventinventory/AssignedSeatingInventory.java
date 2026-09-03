package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

import com.biletflow.biletflow.ticketinventory.domain.common.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHold;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Instant;
import java.util.*;

public final class AssignedSeatingInventory implements InventoryMode {

    private final Map<SeatId, String> seatPriceCategories;
    private final List<SeatHold> holds;

    public AssignedSeatingInventory(Map<SeatId, String> seatPriceCategories, List<SeatHold> holds) {
        Objects.requireNonNull(seatPriceCategories, "Seat map cannot be null");

        if (seatPriceCategories.isEmpty()) {
            throw new IllegalArgumentException("Assigned seating inventory must contain seats");
        }

        this.seatPriceCategories = new HashMap<>(seatPriceCategories);
        this.holds = new ArrayList<>(Objects.requireNonNull(holds, "Holds list cannot be null"));
    }

    public static AssignedSeatingInventory create(Map<SeatId, String> seatPriceCategories) {
        return new AssignedSeatingInventory(seatPriceCategories, List.of());
    }

    public SeatHold holdSeat(TicketTypeId ticketTypeId, SeatId seatId, SessionId sessionId, HoldExpiry holdExpiry, Instant now) {
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
        Objects.requireNonNull(seatId, "SeatId cannot be null");
        Objects.requireNonNull(sessionId, "SessionId cannot be null");
        Objects.requireNonNull(holdExpiry, "HoldExpiry cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        releaseExpiredHolds(now);

        if (!seatPriceCategories.containsKey(seatId)) {
            throw new IllegalArgumentException("Seat does not exist in event inventory: " + seatId);
        }

        boolean seatTaken = holds
            .stream()
            .anyMatch(
                hold ->
                    hold.getSeatId().equals(seatId) && (hold.getStatus() == SeatHoldStatus.HELD || hold.getStatus() == SeatHoldStatus.SOLD)
            );

        if (seatTaken) {
            throw new IllegalStateException("Seat is currently held or sold: " + seatId);
        }

        SeatHold hold = SeatHold.create(ticketTypeId, seatId, sessionId, holdExpiry.calculateExpirationFrom(now));

        holds.add(hold);
        return hold;
    }

    public void confirmHold(SeatHoldId holdId, OrderId orderId, Instant now) {
        findHold(holdId).confirm(orderId, now);
    }

    public void releaseHold(SeatHoldId holdId, SessionId sessionId) {
        findHold(holdId).release(sessionId);
    }

    public void cancelSale(SeatId seatId, OrderId orderId) {
        findSoldSeat(seatId, orderId).markCancelled(orderId);
    }

    public void refundSale(SeatId seatId, OrderId orderId) {
        findSoldSeat(seatId, orderId).markRefunded(orderId);
    }

    public void releaseExpiredHolds(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        for (SeatHold hold : holds) {
            if (hold.isExpiredAt(now)) {
                hold.markExpired();
            }
        }
    }

    public String getPriceCategory(SeatId seatId) {
        Objects.requireNonNull(seatId, "SeatId cannot be null");

        if (!seatPriceCategories.containsKey(seatId)) {
            throw new IllegalArgumentException("Seat does not exist in event inventory: " + seatId);
        }

        return seatPriceCategories.get(seatId);
    }

    public boolean containsPriceCategory(String priceCategory) {
        return seatPriceCategories
            .values()
            .stream()
            .anyMatch(category -> Objects.equals(category, priceCategory));
    }

    public Set<SeatId> getTotalSeats() {
        return Collections.unmodifiableSet(seatPriceCategories.keySet());
    }

    public Map<SeatId, String> getSeatPriceCategories() {
        return Collections.unmodifiableMap(seatPriceCategories);
    }

    public List<SeatHold> getHolds() {
        return Collections.unmodifiableList(holds);
    }

    private SeatHold findHold(SeatHoldId id) {
        Objects.requireNonNull(id, "SeatHoldId cannot be null");

        return holds
            .stream()
            .filter(hold -> hold.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("SeatHold not found: " + id));
    }

    private SeatHold findSoldSeat(SeatId seatId, OrderId orderId) {
        return holds
            .stream()
            .filter(hold -> hold.getSeatId().equals(seatId) && hold.getStatus() == SeatHoldStatus.SOLD && orderId.equals(hold.getOrderId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Sold seat allocation not found for seat: " + seatId));
    }
}
