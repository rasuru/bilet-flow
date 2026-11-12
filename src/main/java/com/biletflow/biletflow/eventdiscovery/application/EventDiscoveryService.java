package com.biletflow.biletflow.eventdiscovery.application;

import com.biletflow.biletflow.eventmanagement.ohs.EventDiscoveryOhs;
import com.biletflow.biletflow.eventmanagement.ohs.dto.DiscoveryEvent;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentsOhs;
import com.biletflow.biletflow.ticketinventory.ohs.TicketInventoryOhs;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventDiscoveryService {

    private final EventDiscoveryOhs events;
    private final TicketInventoryOhs inventory;
    private final PaymentsOhs payments;
    private final Clock clock;

    public EventDiscoveryService(EventDiscoveryOhs events, TicketInventoryOhs inventory, PaymentsOhs payments, Clock clock) {
        this.events = Objects.requireNonNull(events);
        this.inventory = Objects.requireNonNull(inventory);
        this.payments = Objects.requireNonNull(payments);
        this.clock = Objects.requireNonNull(clock);
    }

    public DiscoveryPage discover(int page, int size, String query, String category, boolean availableOnly, boolean descending) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }

        Instant now = clock.instant();
        List<DiscoveryEvent> candidates = events.findCandidates(now, query.strip(), category.strip(), descending);
        long offset = (long) page * size;
        if (!availableOnly) {
            List<DiscoveryItem> items = candidates.stream().skip(offset).limit(size).map(event -> item(event, now)).toList();
            return new DiscoveryPage(items, page, size, candidates.size(), Math.ceilDiv(candidates.size(), size));
        }

        // Availability is live and belongs to two other contexts. Count matching events before applying the page boundary.
        List<DiscoveryItem> items = new ArrayList<>();
        int total = 0;
        for (DiscoveryEvent event : candidates) {
            DiscoveryItem item = item(event, now);
            if (item.salesStatus() == SalesStatus.AVAILABLE) {
                if (total >= offset && items.size() < size) {
                    items.add(item);
                }
                total++;
            }
        }
        return new DiscoveryPage(List.copyOf(items), page, size, total, Math.ceilDiv(total, size));
    }

    private DiscoveryItem item(DiscoveryEvent event, Instant now) {
        return new DiscoveryItem(event.id(), event.title(), event.category(), event.startAt(), event.imageUrl(), salesStatus(event, now));
    }

    private SalesStatus salesStatus(DiscoveryEvent event, Instant now) {
        if (!now.isBefore(event.startAt())) {
            return SalesStatus.REGISTRATION_CLOSED;
        }
        if (event.registrationStartAt() != null && now.isBefore(event.registrationStartAt())) {
            return SalesStatus.COMING_SOON;
        }
        return switch (inventory.salesAvailability(event.id(), now, payments.permitsPaidSales(event.id()))) {
            case AVAILABLE -> SalesStatus.AVAILABLE;
            case SOLD_OUT -> SalesStatus.SOLD_OUT;
            case COMING_SOON -> SalesStatus.COMING_SOON;
            case UNAVAILABLE -> SalesStatus.UNAVAILABLE;
        };
    }

    public enum SalesStatus {
        AVAILABLE,
        SOLD_OUT,
        COMING_SOON,
        REGISTRATION_CLOSED,
        UNAVAILABLE,
    }

    public record DiscoveryItem(UUID id, String title, String category, Instant startAt, String imageUrl, SalesStatus salesStatus) {}

    public record DiscoveryPage(List<DiscoveryItem> items, int page, int size, int totalItems, int totalPages) {}
}
