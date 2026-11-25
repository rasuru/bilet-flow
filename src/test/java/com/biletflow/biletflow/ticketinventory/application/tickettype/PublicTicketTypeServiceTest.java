package com.biletflow.biletflow.ticketinventory.application.tickettype;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.EventInventoryService;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.TicketPurchaseAvailability;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.application.tickettype.port.PaidSalesPort;
import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.GeneralAdmissionInventory;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.SalesWindow;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import com.biletflow.biletflow.ticketinventory.ohs.TicketSalesAvailability;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicTicketTypeServiceTest {
    private final Instant now = Instant.parse("2026-11-25T12:00:00Z");
    private final UUID event = UUID.randomUUID();
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final TicketTypeRepository types = mock(TicketTypeRepository.class);
    private final EventInventoryRepository inventories = mock(EventInventoryRepository.class);
    private final EventManagementPort events = mock(EventManagementPort.class);
    private final PaidSalesPort payments = mock(PaidSalesPort.class);
    private final PublicTicketTypeService publicTypes = new PublicTicketTypeService(types, inventories, events, payments, clock);
    private final EventInventoryService discoveryInventory = new EventInventoryService(inventories, types, clock);

    @Test
    void inactivePaidOnlyEventHasNoPurchasableTicketsAndActivationEnablesThem() {
        var paid = ticket(1000);
        prepare(List.of(paid));
        when(payments.permitsPaidSales(event)).thenReturn(false, true);

        var before = publicTypes.list(event).getFirst();
        assertFalse(before.purchasable());
        assertEquals(TicketPurchaseAvailability.SALES_NOT_ACTIVE, before.purchaseStatus());
        assertEquals(TicketSalesAvailability.UNAVAILABLE, discoveryInventory.salesAvailability(event, now, false));
        assertTrue(publicTypes.list(event).getFirst().purchasable());
        assertEquals(TicketSalesAvailability.AVAILABLE, discoveryInventory.salesAvailability(event, now, true));
    }

    @Test
    void freeTicketsRemainAvailableAndHiddenTicketsAreNotReturned() {
        var free = ticket(0);
        var paid = ticket(1000);
        var hidden = ticket(0);
        hidden.hide();
        prepare(List.of(free, paid, hidden));
        var result = publicTypes.list(event);
        assertEquals(2, result.size());
        assertTrue(result.get(0).purchasable());
        assertFalse(result.get(1).purchasable());
        assertEquals(TicketSalesAvailability.AVAILABLE, discoveryInventory.salesAvailability(event, now, false));
    }

    @Test
    void closedRegistrationDisablesEvenFreeTicketsAndPrivateEventIsNotPublic() {
        prepare(List.of(ticket(0)));
        when(events.publicSalesScope(event, now)).thenReturn(new EventManagementPort.PublicSalesScope(false, true));
        var closed = publicTypes.list(event).getFirst();
        assertFalse(closed.purchasable());
        assertEquals(TicketPurchaseAvailability.REGISTRATION_CLOSED, closed.purchaseStatus());
        when(events.publicSalesScope(event, now)).thenReturn(new EventManagementPort.PublicSalesScope(false, false));
        assertThrows(EntityNotFoundException.class, () -> publicTypes.list(event));
    }

    private TicketType ticket(long price) {
        return TicketType.createGeneralAdmission(event, "Ticket", "", Money.kzt(price), 1,
            new SalesWindow(now.minusSeconds(60), now.plusSeconds(600)));
    }

    private void prepare(List<TicketType> tickets) {
        var ga = GeneralAdmissionInventory.empty();
        tickets.forEach(type -> ga.configureTicketType(type.getId(), 5));
        when(types.findAllByEventId(event)).thenReturn(tickets);
        when(inventories.findByEventId(event)).thenReturn(Optional.of(EventInventory.create(event, HoldExpiry.defaultExpiry(), ga)));
        when(events.publicSalesScope(event, now)).thenReturn(new EventManagementPort.PublicSalesScope(true, true));
    }
}

