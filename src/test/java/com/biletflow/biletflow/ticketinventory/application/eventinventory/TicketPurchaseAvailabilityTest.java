package com.biletflow.biletflow.ticketinventory.application.eventinventory;

import static com.biletflow.biletflow.ticketinventory.application.eventinventory.TicketPurchaseAvailability.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.AssignedSeatingInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.GeneralAdmissionInventory;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.SalesWindow;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketPurchaseAvailabilityTest {
    private final Instant now = Instant.parse("2026-11-25T12:00:00Z");
    private final UUID eventId = UUID.randomUUID();
    private final SalesWindow open = new SalesWindow(now.minusSeconds(60), now.plusSeconds(3600));

    @Test
    void paidAndFreeTypesInSameEventHaveIndependentEligibility() {
        var free = gaType(0, open);
        var paid = gaType(1000, open);
        var ga = GeneralAdmissionInventory.empty();
        ga.configureTicketType(free.getId(), 5);
        ga.configureTicketType(paid.getId(), 5);

        assertEquals(AVAILABLE, evaluate(free, ga, now, true, false));
        assertEquals(SALES_NOT_ACTIVE, evaluate(paid, ga, now, true, false));
        assertEquals(AVAILABLE, evaluate(paid, ga, now, true, true));
        assertEquals(REGISTRATION_CLOSED, evaluate(free, ga, now, false, true));
    }

    @Test
    void saleWindowAndHiddenPausedClosedTypesAreNotPurchasable() {
        var future = gaType(0, new SalesWindow(now.plusSeconds(60), now.plusSeconds(120)));
        var past = gaType(0, new SalesWindow(now.minusSeconds(120), now.minusSeconds(60)));
        var type = gaType(0, open);
        var ga = GeneralAdmissionInventory.empty();
        ga.configureTicketType(future.getId(), 1);
        ga.configureTicketType(past.getId(), 1);
        ga.configureTicketType(type.getId(), 1);
        assertEquals(COMING_SOON, evaluate(future, ga, now, true, false));
        assertEquals(SALES_CLOSED, evaluate(past, ga, now, true, false));
        type.hide();
        assertEquals(UNAVAILABLE, evaluate(type, ga, now, true, true));
        type.unhide();
        type.pause();
        assertEquals(UNAVAILABLE, evaluate(type, ga, now, true, true));
        type.close();
        assertEquals(SALES_CLOSED, evaluate(type, ga, now, true, true));
    }

    @Test
    void holdsConsumeGeneralAdmissionStockUntilTheyExpire() {
        var type = gaType(0, open);
        var ga = GeneralAdmissionInventory.empty();
        ga.configureTicketType(type.getId(), 1);
        var inventory = EventInventory.create(eventId, HoldExpiry.defaultExpiry(), ga);
        inventory.reserveGA(type.getId(), 1, new SessionId("checkout"), now);
        assertEquals(SOLD_OUT, evaluate(type, ga, now, true, false));
        var later = now.plusSeconds(601);
        inventory.cleanUpExpiredHolds(later);
        assertEquals(AVAILABLE, evaluate(type, ga, later, true, false));
    }

    @Test
    void assignedSeatsRequireActivationAndStockInTheirOwnCategory() {
        var free = TicketType.createAssignedSeating(eventId, "Free", "", Money.zeroKzt(), 1, open, "FREE");
        var paid = TicketType.createAssignedSeating(eventId, "Paid", "", Money.kzt(1000), 1, open, "PREMIUM");
        var freeSeat = new SeatId(UUID.randomUUID());
        var paidSeat = new SeatId(UUID.randomUUID());
        var assigned = AssignedSeatingInventory.create(Map.of(freeSeat, "FREE", paidSeat, "PREMIUM"));
        var inventory = EventInventory.create(eventId, HoldExpiry.defaultExpiry(), assigned);
        assertEquals(AVAILABLE, evaluate(free, assigned, now, true, false));
        assertEquals(SALES_NOT_ACTIVE, evaluate(paid, assigned, now, true, false));
        assertEquals(AVAILABLE, evaluate(paid, assigned, now, true, true));
        inventory.holdSeat(paid.getId(), paidSeat, new SessionId("checkout"), now);
        assertEquals(SOLD_OUT, evaluate(paid, assigned, now, true, true));
        assertEquals(AVAILABLE, evaluate(free, assigned, now, true, false));
        var later = now.plusSeconds(601);
        inventory.cleanUpExpiredHolds(later);
        assertEquals(AVAILABLE, evaluate(paid, assigned, later, true, true));
    }

    private TicketType gaType(long price, SalesWindow window) {
        return TicketType.createGeneralAdmission(eventId, "Ticket", "", Money.kzt(price), 1, window);
    }
}

