package com.biletflow.biletflow.ticketinventory.application.analytics;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.ordercheckout.ohs.OrderAnalyticsOhs;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryCounters;
import com.biletflow.biletflow.ticketinventory.domain.ticket.Ticket;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketRepository;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventAnalyticsService {

    private static final String CURRENCY = "KZT";

    private final CurrentActor actor;
    private final EventManagementOhs events;
    private final TicketRepository tickets;
    private final TicketTypeRepository ticketTypes;
    private final EventInventoryRepository inventories;
    private final OrderAnalyticsOhs orders;

    public EventAnalyticsService(CurrentActor actor, EventManagementOhs events, TicketRepository tickets,
                                 TicketTypeRepository ticketTypes, EventInventoryRepository inventories,
                                 OrderAnalyticsOhs orders) {
        this.actor = Objects.requireNonNull(actor);
        this.events = Objects.requireNonNull(events);
        this.tickets = Objects.requireNonNull(tickets);
        this.ticketTypes = Objects.requireNonNull(ticketTypes);
        this.inventories = Objects.requireNonNull(inventories);
        this.orders = Objects.requireNonNull(orders);
    }

    public EventAnalyticsView get(UUID eventId, LocalDate from, LocalDate to) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Long userId = actor.requireUserId();
        if (!events.canManageTicketing(eventId, userId)) throw new AuthorizationException("Only the event organizer can view analytics");
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("from must be on or before to");

        List<Ticket> allEventTickets = tickets.findAllByEventId(eventId);
        List<Ticket> eventTickets = allEventTickets.stream().filter(ticket -> {
            LocalDate issuedDate = ticket.getIssuedAt().atZone(ZoneOffset.UTC).toLocalDate();
            return (from == null || !issuedDate.isBefore(from)) && (to == null || !issuedDate.isAfter(to));
        }).toList();
        List<TicketType> types = ticketTypes.findAllByEventId(eventId);
        EventInventory inventory = inventories.findByEventId(eventId)
            .orElseThrow(() -> new EntityNotFoundException("Event inventory not found: " + eventId));

        Map<UUID, Long> capacityByType = capacities(inventory, types);
        Map<UUID, OrderAnalyticsOhs.OrderSnapshot> orderById = orders.findEventOrders(eventId).stream()
            .collect(Collectors.toMap(OrderAnalyticsOhs.OrderSnapshot::orderId, order -> order));
        Map<UUID, List<Ticket>> ticketGroups = eventTickets.stream().collect(Collectors.groupingBy(t -> t.getTicketTypeId().value()));
        Map<UUID, BigDecimal> typeRevenue = new HashMap<>();
        Map<UUID, BigDecimal> campaignRevenue = new HashMap<>();
        Map<UUID, Long> campaignTicketCounts = new HashMap<>();
        Map<LocalDate, Long> dailyTicketCounts = new TreeMap<>();
        Map<LocalDate, BigDecimal> dailyRevenue = new TreeMap<>();

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalSold = 0;
        long totalRefunded = 0;
        long totalCheckedIn = 0;

        for (Ticket ticket : eventTickets) {
            TicketStatus status = ticket.getStatus();
            boolean active = status == TicketStatus.VALID || status == TicketStatus.CHECKED_IN || status == TicketStatus.REFUND_PENDING;
            if (active) totalSold++;
            if (status == TicketStatus.REFUNDED) totalRefunded++;
            if (status == TicketStatus.CHECKED_IN) totalCheckedIn++;

            LocalDate issuedDate = ticket.getIssuedAt().atZone(ZoneOffset.UTC).toLocalDate();
            OrderAnalyticsOhs.OrderSnapshot order = orderById.get(ticket.getOrderId().value());
            if (active) dailyTicketCounts.merge(issuedDate, 1L, Long::sum);
            if (active && order != null && order.promotionId() != null) {
                campaignTicketCounts.merge(order.promotionId(), 1L, Long::sum);
            }
            BigDecimal ticketRevenue = revenueForTicket(ticket, order);
            if (ticketRevenue.signum() > 0) {
                dailyRevenue.merge(issuedDate, ticketRevenue, BigDecimal::add);
                typeRevenue.merge(ticket.getTicketTypeId().value(), ticketRevenue, BigDecimal::add);
                totalRevenue = totalRevenue.add(ticketRevenue);
                if (order != null && order.promotionId() != null) {
                    campaignRevenue.merge(order.promotionId(), ticketRevenue, BigDecimal::add);
                }
            }
        }

        List<EventAnalyticsView.TicketTypeSales> typeViews = types.stream()
            .map(type -> {
                List<Ticket> group = ticketGroups.getOrDefault(type.getId().value(), List.of());
                long sold = group.stream().filter(t -> t.getStatus() == TicketStatus.VALID || t.getStatus() == TicketStatus.CHECKED_IN || t.getStatus() == TicketStatus.REFUND_PENDING).count();
                long refunded = group.stream().filter(t -> t.getStatus() == TicketStatus.REFUNDED).count();
                long checkedIn = group.stream().filter(t -> t.getStatus() == TicketStatus.CHECKED_IN).count();
                return new EventAnalyticsView.TicketTypeSales(type.getId().value(), type.getName(), capacityByType.getOrDefault(type.getId().value(), 0L),
                    sold, refunded, checkedIn, typeRevenue.getOrDefault(type.getId().value(), BigDecimal.ZERO));
            }).sorted(Comparator.comparing(EventAnalyticsView.TicketTypeSales::name)).toList();

        List<EventAnalyticsView.DailySales> daily = dailyTicketCounts.keySet().stream()
            .map(date -> new EventAnalyticsView.DailySales(date, dailyTicketCounts.get(date), dailyRevenue.getOrDefault(date, BigDecimal.ZERO)))
            .toList();
        List<EventAnalyticsView.PromotionSales> campaignViews = campaignTicketCounts.keySet().stream()
            .map(id -> new EventAnalyticsView.PromotionSales(id, campaignTicketCounts.get(id), campaignRevenue.getOrDefault(id, BigDecimal.ZERO)))
            .sorted(Comparator.comparing(v -> v.promotionId().toString())).toList();

        long capacity = capacityByType.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal attendanceRate = totalSold == 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(totalCheckedIn).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalSold), 2, RoundingMode.HALF_UP);
        return new EventAnalyticsView(eventId, capacity, totalSold, totalRefunded, totalCheckedIn, attendanceRate,
            totalRevenue.setScale(2, RoundingMode.HALF_UP), CURRENCY, daily, typeViews, campaignViews);
    }

    private BigDecimal revenueForTicket(Ticket ticket, OrderAnalyticsOhs.OrderSnapshot order) {
        if (ticket.getStatus() != TicketStatus.VALID && ticket.getStatus() != TicketStatus.CHECKED_IN
            && ticket.getStatus() != TicketStatus.REFUND_PENDING) return BigDecimal.ZERO;
        if (order == null || !"COMPLETED".equals(order.status())) return BigDecimal.ZERO;
        List<OrderAnalyticsOhs.OrderLine> matching = order.lines().stream()
            .filter(line -> line.ticketTypeId().equals(ticket.getTicketTypeId().value())).toList();
        if (matching.isEmpty()) return BigDecimal.ZERO;
        BigDecimal gross = matching.stream().map(OrderAnalyticsOhs.OrderLine::priceAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGross = order.lines().stream().map(OrderAnalyticsOhs.OrderLine::priceAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalGross.signum() == 0) return BigDecimal.ZERO;
        BigDecimal matchingUnitPrice = gross.divide(BigDecimal.valueOf(matching.size()), 8, RoundingMode.HALF_UP);
        BigDecimal netFactor = order.totalAmount().subtract(order.discountAmount()).divide(totalGross, 8, RoundingMode.HALF_UP);
        // Ticket rows are issued one per order line; allocate the order-level discount proportionally by line price.
        return matchingUnitPrice.multiply(netFactor).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<UUID, Long> capacities(EventInventory inventory, List<TicketType> types) {
        Map<UUID, Long> result = new HashMap<>();
        if (inventory.getMode() instanceof GeneralAdmissionInventory ga) {
            ga.getCounters().forEach((id, counters) -> result.put(id.value(), (long) counters.totalCapacity()));
        } else if (inventory.getMode() instanceof AssignedSeatingInventory assigned) {
            Map<String, Long> categoryCounts = assigned.getSeatPriceCategories().values().stream()
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
            for (TicketType type : types) result.put(type.getId().value(), categoryCounts.getOrDefault(type.getPriceCategory(), 0L));
        }
        return result;
    }
}
