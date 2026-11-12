package com.biletflow.biletflow.eventdiscovery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.eventdiscovery.application.EventDiscoveryService.SalesStatus;
import com.biletflow.biletflow.eventmanagement.ohs.EventDiscoveryOhs;
import com.biletflow.biletflow.eventmanagement.ohs.dto.DiscoveryEvent;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentsOhs;
import com.biletflow.biletflow.ticketinventory.ohs.TicketInventoryOhs;
import com.biletflow.biletflow.ticketinventory.ohs.TicketSalesAvailability;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventDiscoveryServiceTest {

    @Test
    void availableOnlyFiltersBeforePaginatingAndCountsAllMatches() {
        Instant now = Instant.parse("2026-11-12T12:00:00Z");
        DiscoveryEvent soldOut = event("Sold out", now.plusSeconds(100), null);
        DiscoveryEvent first = event("First available", now.plusSeconds(200), null);
        DiscoveryEvent upcoming = event("Coming soon", now.plusSeconds(300), now.plusSeconds(60));
        DiscoveryEvent second = event("Second available", now.plusSeconds(400), null);
        EventDiscoveryOhs events = mock(EventDiscoveryOhs.class);
        PaymentsOhs payments = mock(PaymentsOhs.class);
        TicketInventoryOhs inventory = mock(TicketInventoryOhs.class);
        when(events.findCandidates(now, "jazz", "Music", false)).thenReturn(List.of(soldOut, first, upcoming, second));
        when(inventory.salesAvailability(soldOut.id(), now, false)).thenReturn(TicketSalesAvailability.SOLD_OUT);
        when(inventory.salesAvailability(first.id(), now, false)).thenReturn(TicketSalesAvailability.AVAILABLE);
        when(payments.permitsPaidSales(second.id())).thenReturn(true);
        when(inventory.salesAvailability(second.id(), now, true)).thenReturn(TicketSalesAvailability.AVAILABLE);
        EventDiscoveryService service = new EventDiscoveryService(events, inventory, payments, Clock.fixed(now, ZoneOffset.UTC));

        var defaultPage = service.discover(0, 20, "jazz", "Music", false, false);
        assertEquals(
            List.of(SalesStatus.SOLD_OUT, SalesStatus.AVAILABLE, SalesStatus.COMING_SOON, SalesStatus.AVAILABLE),
            defaultPage.items().stream().map(EventDiscoveryService.DiscoveryItem::salesStatus).toList()
        );

        var filteredPage = service.discover(1, 1, "jazz", "Music", true, false);
        assertEquals(List.of(second.id()), filteredPage.items().stream().map(EventDiscoveryService.DiscoveryItem::id).toList());
        assertEquals("Jazz Hall", filteredPage.items().getFirst().venueName());
        assertEquals("10 Dostyq Street", filteredPage.items().getFirst().venueAddress());
        assertEquals(2, filteredPage.totalItems());
        assertEquals(2, filteredPage.totalPages());
    }

    private DiscoveryEvent event(String title, Instant startsAt, Instant registrationStartsAt) {
        return new DiscoveryEvent(
            UUID.randomUUID(),
            title,
            "Music",
            startsAt,
            "https://example.com/image.jpg",
            "Jazz Hall",
            "10 Dostyq Street",
            registrationStartsAt
        );
    }
}
