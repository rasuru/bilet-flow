package com.biletflow.biletflow.ticketinventory.application.eventinventory;

import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.AssignedSeatingInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.GeneralAdmissionInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.InventoryMode;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeStatus;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Read-side purchasing policy. Checkout still validates and reserves inventory atomically. */
public enum TicketPurchaseAvailability {
    AVAILABLE,
    SALES_NOT_ACTIVE,
    REGISTRATION_CLOSED,
    COMING_SOON,
    SOLD_OUT,
    SALES_CLOSED,
    UNAVAILABLE;

    /** Inventory must be a read-only snapshot with expired holds released. */
    public static TicketPurchaseAvailability evaluate(
        TicketType type, InventoryMode mode, Instant now, boolean registrationOpen, boolean paidSalesPermitted
    ) {
        if (type.getStatus() == TicketTypeStatus.CLOSED) return SALES_CLOSED;
        if (type.getStatus() != TicketTypeStatus.ACTIVE) return UNAVAILABLE;
        if (!registrationOpen) return REGISTRATION_CLOSED;
        if (now.isAfter(type.getSalesWindow().endAt())) return SALES_CLOSED;
        if (!type.isFree() && !paidSalesPermitted) return SALES_NOT_ACTIVE;
        boolean inStock = hasStock(type, mode);
        if (now.isBefore(type.getSalesWindow().startAt())) return inStock ? COMING_SOON : UNAVAILABLE;
        if (!type.isPurchasableAt(now)) return SALES_CLOSED;
        return inStock ? AVAILABLE : SOLD_OUT;
    }

    private static boolean hasStock(TicketType type, InventoryMode mode) {
        if (mode instanceof GeneralAdmissionInventory ga) {
            var counters = ga.getCounters().get(type.getId());
            return !type.isAssignedSeatingType() && counters != null && counters.available() > 0;
        }
        if (!(mode instanceof AssignedSeatingInventory assigned) || !type.isAssignedSeatingType()) return false;
        Set<SeatId> occupied = new HashSet<>();
        for (var hold : assigned.getHolds()) {
            if (hold.getStatus() == SeatHoldStatus.HELD || hold.getStatus() == SeatHoldStatus.SOLD) {
                occupied.add(hold.getSeatId());
            }
        }
        return assigned.getSeatPriceCategories().entrySet().stream().anyMatch(seat ->
            !occupied.contains(seat.getKey()) && Objects.equals(type.getPriceCategory(), seat.getValue())
        );
    }
}

