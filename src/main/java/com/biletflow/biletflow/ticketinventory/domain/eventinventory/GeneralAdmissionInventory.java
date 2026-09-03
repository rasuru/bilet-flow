package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryCounters;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservation;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservationId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Instant;
import java.util.*;

public final class GeneralAdmissionInventory implements InventoryMode {

    private final Map<TicketTypeId, InventoryCounters> counters;
    private final List<InventoryReservation> reservations;

    public GeneralAdmissionInventory(Map<TicketTypeId, InventoryCounters> counters, List<InventoryReservation> reservations) {
        this.counters = new HashMap<>(Objects.requireNonNull(counters, "Counters map cannot be null"));
        this.reservations = new ArrayList<>(Objects.requireNonNull(reservations, "Reservations list cannot be null"));
    }

    public static GeneralAdmissionInventory empty() {
        return new GeneralAdmissionInventory(Map.of(), List.of());
    }

    public InventoryReservation reserve(TicketTypeId ticketTypeId, int quantity, SessionId sessionId, HoldExpiry holdExpiry, Instant now) {
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
        Objects.requireNonNull(sessionId, "SessionId cannot be null");
        Objects.requireNonNull(holdExpiry, "HoldExpiry cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        // Lazy expiry: stale reservations must not block new inventory.
        releaseExpiredReservations(now);

        InventoryCounters current = requireCounters(ticketTypeId);
        counters.put(ticketTypeId, current.withReservation(quantity));

        InventoryReservation reservation = InventoryReservation.create(
            ticketTypeId,
            quantity,
            sessionId,
            holdExpiry.calculateExpirationFrom(now)
        );

        reservations.add(reservation);
        return reservation;
    }

    public void confirmReservation(InventoryReservationId reservationId, OrderId orderId, Instant now) {
        InventoryReservation reservation = findReservation(reservationId);

        boolean newlyConfirmed = reservation.confirm(orderId, now);
        if (!newlyConfirmed) {
            return;
        }

        InventoryCounters current = requireCounters(reservation.getTicketTypeId());
        counters.put(reservation.getTicketTypeId(), current.withConfirmation(reservation.getQuantity()));
    }

    public void releaseReservation(InventoryReservationId reservationId, SessionId sessionId) {
        InventoryReservation reservation = findReservation(reservationId);
        reservation.release(sessionId);

        InventoryCounters current = requireCounters(reservation.getTicketTypeId());
        counters.put(reservation.getTicketTypeId(), current.withReservationRelease(reservation.getQuantity()));
    }

    public void releaseExpiredReservations(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        for (InventoryReservation reservation : reservations) {
            if (!reservation.isExpiredAt(now)) {
                continue;
            }

            reservation.markExpired();

            InventoryCounters current = requireCounters(reservation.getTicketTypeId());
            counters.put(reservation.getTicketTypeId(), current.withReservationRelease(reservation.getQuantity()));
        }
    }

    public void configureTicketType(TicketTypeId ticketTypeId, int totalCapacity) {
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");

        if (counters.containsKey(ticketTypeId)) {
            throw new IllegalStateException("Capacity already configured for TicketTypeId: " + ticketTypeId);
        }

        counters.put(ticketTypeId, InventoryCounters.initial(totalCapacity));
    }

    public void changeTicketTypeCapacity(TicketTypeId ticketTypeId, int newCapacity) {
        InventoryCounters current = requireCounters(ticketTypeId);
        counters.put(ticketTypeId, current.withCapacity(newCapacity));
    }

    public void reverseSale(TicketTypeId ticketTypeId, int quantity) {
        InventoryCounters current = requireCounters(ticketTypeId);
        counters.put(ticketTypeId, current.withSaleReversal(quantity));
    }

    private InventoryCounters requireCounters(TicketTypeId ticketTypeId) {
        InventoryCounters current = counters.get(ticketTypeId);

        if (current == null) {
            throw new IllegalArgumentException("No capacity configured for TicketTypeId: " + ticketTypeId);
        }

        return current;
    }

    private InventoryReservation findReservation(InventoryReservationId id) {
        Objects.requireNonNull(id, "InventoryReservationId cannot be null");

        return reservations
            .stream()
            .filter(reservation -> reservation.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));
    }

    public Map<TicketTypeId, InventoryCounters> getCounters() {
        return Collections.unmodifiableMap(counters);
    }

    public List<InventoryReservation> getReservations() {
        return Collections.unmodifiableList(reservations);
    }
}
