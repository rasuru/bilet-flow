package com.biletflow.biletflow.ticketinventory.application.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.ohs.OrderAnalyticsOhs;
import com.biletflow.biletflow.ticketinventory.domain.common.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryCounters;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class EventAnalyticsServiceTest {

    @Test
    void combinesTicketInventoryAndOrderPricingForOrganizerDashboard() {
        UUID eventId = UUID.randomUUID();
        TicketTypeId typeId = TicketTypeId.generate();
        UUID orderId = UUID.randomUUID();
        Ticket ticket = new Ticket(TicketId.generate(), typeId, eventId, new OrderId(orderId),
            new AttendeeEmail("buyer@example.com"), 5L, null, TicketCode.generate(), TicketStatus.CHECKED_IN,
            Instant.parse("2026-11-21T10:00:00Z"));

        CurrentActor actor = () -> Optional.of(5L);
        EventManagementOhs events = mock(EventManagementOhs.class);
        when(events.canManageTicketing(eventId, 5L)).thenReturn(true);
        TicketRepository tickets = mock(TicketRepository.class);
        when(tickets.findAllByEventId(eventId)).thenReturn(List.of(ticket));
        TicketTypeRepository types = mock(TicketTypeRepository.class);
        TicketType type = mock(TicketType.class);
        when(type.getId()).thenReturn(typeId);
        when(type.getName()).thenReturn("Standard");
        when(types.findAllByEventId(eventId)).thenReturn(List.of(type));
        EventInventoryRepository inventoryRepository = mock(EventInventoryRepository.class);
        var counters = new InventoryCounters(100, 0, 1);
        var inventory = new EventInventory(new EventInventoryId(UUID.randomUUID()), eventId, new HoldExpiry(java.time.Duration.ofMinutes(10)),
            new GeneralAdmissionInventory(Map.of(typeId, counters), List.of()));
        when(inventoryRepository.findByEventId(eventId)).thenReturn(Optional.of(inventory));
        OrderAnalyticsOhs orders = mock(OrderAnalyticsOhs.class);
        when(orders.findEventOrders(eventId)).thenReturn(List.of(new OrderAnalyticsOhs.OrderSnapshot(
            orderId, "COMPLETED", null, new BigDecimal("100.00"), new BigDecimal("10.00"), "KZT",
            List.of(new OrderAnalyticsOhs.OrderLine(typeId.value(), new BigDecimal("100.00"), "KZT"))
        )));

        var result = new EventAnalyticsService(actor, events, tickets, types, inventoryRepository, orders).get(eventId, null, null);

        assertEquals(100, result.capacity());
        assertEquals(1, result.sold());
        assertEquals(1, result.checkedIn());
        assertEquals(new BigDecimal("100.00"), result.attendanceRatePercent());
        assertEquals(new BigDecimal("90.00"), result.netRevenue());
        assertEquals(1, result.salesOverTime().size());
        assertEquals(1, result.ticketTypes().size());
        assertEquals(new BigDecimal("90.00"), result.ticketTypes().getFirst().revenue());
    }
}
