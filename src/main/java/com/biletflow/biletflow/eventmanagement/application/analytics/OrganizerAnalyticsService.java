package com.biletflow.biletflow.eventmanagement.application.analytics;

import com.biletflow.biletflow.eventmanagement.application.analytics.OrganizerAnalyticsView.*;
import com.biletflow.biletflow.eventmanagement.application.socialevent.EventQueryService;
import com.biletflow.biletflow.eventmanagement.application.socialevent.view.ManagedEventView;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import com.biletflow.biletflow.ordercheckout.ohs.OrderAnalyticsOhs;
import com.biletflow.biletflow.ordercheckout.ohs.OrderAnalyticsOhs.SaleLine;
import com.biletflow.biletflow.paymentsmonetization.ohs.RefundAnalyticsOhs;
import com.biletflow.biletflow.promotions.ohs.PromotionAnalyticsOhs;
import com.biletflow.biletflow.promotions.ohs.PromotionAnalyticsOhs.Redemption;
import com.biletflow.biletflow.ticketinventory.ohs.TicketAnalyticsOhs;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizerAnalyticsService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Almaty");

    private final EventQueryService events;
    private final OrderAnalyticsOhs orders;
    private final TicketAnalyticsOhs tickets;
    private final RefundAnalyticsOhs refunds;
    private final PromotionAnalyticsOhs promotions;
    private final Clock clock;

    public OrganizerAnalyticsService(
        EventQueryService events,
        OrderAnalyticsOhs orders,
        TicketAnalyticsOhs tickets,
        RefundAnalyticsOhs refunds,
        PromotionAnalyticsOhs promotions,
        Clock clock
    ) {
        this.events = events;
        this.orders = orders;
        this.tickets = tickets;
        this.refunds = refunds;
        this.promotions = promotions;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrganizerAnalyticsView report(UUID eventId, LocalDate from, LocalDate through, UUID ticketTypeId) {
        return report(events.getForManagement(new SocialEventId(eventId)), from, through, ticketTypeId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrganizerAnalyticsOverview overview() {
        var sales = new SalesTotal();
        var campaignSales = new SalesTotal();
        long capacity = 0;
        long sold = 0;
        long remaining = 0;
        long held = 0;
        long eligibleEvents = 0;
        long registered = 0;
        long checkedIn = 0;
        long redemptions = 0;

        for (var event : events.listMine()) {
            var report = report(event, null, null, null);
            var inventory = report.inventory();
            capacity += inventory.capacity();
            sold += inventory.sold();
            remaining += inventory.remaining();
            held += inventory.held();
            sales.add(report.sales());

            if (report.attendance() != null) {
                eligibleEvents++;
                registered += report.attendance().registered();
                checkedIn += report.attendance().checkedIn();
            }
            for (var campaign : report.campaignSales()) {
                redemptions += campaign.redemptions();
                campaignSales.add(campaign.sales());
            }
        }

        return new OrganizerAnalyticsOverview(
            new OrganizerAnalyticsOverview.Inventory(capacity, sold, remaining, held),
            sales.view(),
            eligibleEvents == 0 ? null : new Attendance(registered, checkedIn, registered - checkedIn),
            new OrganizerAnalyticsOverview.Campaigns(redemptions, campaignSales.view())
        );
    }

    private OrganizerAnalyticsView report(ManagedEventView event, LocalDate from, LocalDate through, UUID ticketTypeId) {
        UUID eventId = event.id();
        if (from != null && through != null && from.isAfter(through)) {
            throw new IllegalArgumentException("The end date must be on or after the start date.");
        }
        Instant start = from == null ? null : from.atStartOfDay(REPORT_ZONE).toInstant();
        Instant end = through == null ? null : through.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
        var inventory = tickets.inventory(eventId, ticketTypeId);

        var ordersById = orders.issuedSaleLines(eventId, start, end).stream().collect(Collectors.groupingBy(SaleLine::orderId));
        var refundsByOrder = refunds.confirmedTicketRefunds(eventId);
        var promotionsByOrder = promotions
            .confirmedRedemptions(eventId)
            .stream()
            .collect(Collectors.toMap(Redemption::orderId, redemption -> redemption));

        var total = new SalesTotal();
        var days = new TreeMap<LocalDate, SalesTotal>();
        var types = new LinkedHashMap<UUID, SalesTotal>();
        for (var type : inventory.ticketTypes()) {
            if (ticketTypeId == null || ticketTypeId.equals(type.id())) types.put(type.id(), new SalesTotal());
        }
        var campaigns = new LinkedHashMap<UUID, CampaignTotal>();
        long undated = 0;
        for (var order : ordersById.entrySet()) {
            var redemption = promotionsByOrder.get(order.getKey());
            var lines = order.getValue();
            var first = lines.getFirst();
            var discount = first.orderDiscountKzt();
            var refund = refundsByOrder.getOrDefault(order.getKey(), BigDecimal.ZERO);
            var orderGross = gross(lines);
            var eligibleGross =
                redemption == null
                    ? orderGross
                    : gross(
                          lines
                              .stream()
                              .filter(line -> redemption.appliesTo(line.ticketTypeId()))
                              .toList()
                      );
            var allocatedDiscount = BigDecimal.ZERO;
            var allocatedRefund = BigDecimal.ZERO;
            var runningEligibleGross = BigDecimal.ZERO;
            var runningNet = BigDecimal.ZERO;
            var attributedSale = new SalesTotal();
            LocalDate date = first.orderedAt() == null ? null : first.orderedAt().atZone(REPORT_ZONE).toLocalDate();

            // Allocate at order level before filtering. Cumulative rounding preserves exact order totals across types.
            for (var line : lines) {
                boolean attributed = redemption != null && redemption.appliesTo(line.ticketTypeId());
                if (redemption == null || attributed || eligibleGross.signum() == 0) {
                    runningEligibleGross = runningEligibleGross.add(line.grossKzt());
                }
                var discountThroughLine = share(discount, runningEligibleGross, eligibleGross.signum() == 0 ? orderGross : eligibleGross);
                var lineDiscount = discountThroughLine.subtract(allocatedDiscount);
                allocatedDiscount = discountThroughLine;
                runningNet = runningNet.add(line.grossKzt().subtract(lineDiscount));
                var refundThroughLine = share(refund, runningNet, orderGross.subtract(discount));
                var lineRefund = refundThroughLine.subtract(allocatedRefund);
                allocatedRefund = refundThroughLine;

                if (ticketTypeId != null && !ticketTypeId.equals(line.ticketTypeId())) continue;
                var sale = new Sales(
                    line.quantity(),
                    line.grossKzt(),
                    lineDiscount,
                    lineRefund,
                    line.grossKzt().subtract(lineDiscount).subtract(lineRefund)
                );
                total.add(sale);
                types.computeIfAbsent(line.ticketTypeId(), ignored -> new SalesTotal()).add(sale);
                if (date == null) undated += line.quantity();
                else days.computeIfAbsent(date, ignored -> new SalesTotal()).add(sale);
                if (attributed) attributedSale.add(sale);
            }
            if (attributedSale.tickets > 0) {
                campaigns
                    .computeIfAbsent(redemption.campaignId(), ignored -> new CampaignTotal(redemption.campaign()))
                    .add(redemption.code(), attributedSale.view());
            }
        }

        Attendance attendance = null;
        if (!clock.instant().isBefore(event.startAt())) {
            long registered = 0;
            long checkedIn = 0;
            for (var counts : tickets.attendance(eventId, ticketTypeId)) {
                if (!ordersById.containsKey(counts.orderId())) continue;
                registered += counts.registered();
                checkedIn += counts.checkedIn();
            }
            attendance = new Attendance(registered, checkedIn, registered - checkedIn);
        }

        return new OrganizerAnalyticsView(
            inventory,
            total.view(),
            days
                .entrySet()
                .stream()
                .map(entry -> new DailySales(entry.getKey(), entry.getValue().view()))
                .toList(),
            types
                .entrySet()
                .stream()
                .map(entry -> new TicketSales(entry.getKey(), entry.getValue().view()))
                .toList(),
            campaigns
                .entrySet()
                .stream()
                .map(entry -> entry.getValue().view(entry.getKey()))
                .sorted(Comparator.comparing(CampaignSales::name).thenComparing(CampaignSales::id))
                .toList(),
            attendance,
            undated
        );
    }

    private static BigDecimal gross(List<SaleLine> lines) {
        return lines.stream().map(SaleLine::grossKzt).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal share(BigDecimal amount, BigDecimal part, BigDecimal total) {
        if (amount.signum() == 0 || total.signum() == 0) return BigDecimal.ZERO;
        return amount.multiply(part).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static final class SalesTotal {

        private long tickets;
        private BigDecimal gross = BigDecimal.ZERO;
        private BigDecimal discounts = BigDecimal.ZERO;
        private BigDecimal refunds = BigDecimal.ZERO;

        void add(Sales sales) {
            tickets += sales.tickets();
            gross = gross.add(sales.grossKzt());
            discounts = discounts.add(sales.discountsKzt());
            refunds = refunds.add(sales.refundsKzt());
        }

        Sales view() {
            return new Sales(tickets, gross, discounts, refunds, gross.subtract(discounts).subtract(refunds));
        }
    }

    private static final class CampaignTotal {

        private final String name;
        private final SalesTotal sales = new SalesTotal();
        private final Map<String, CodeTotal> codes = new TreeMap<>();
        private long redemptions;

        CampaignTotal(String name) {
            this.name = name;
        }

        void add(String promoCode, Sales attributed) {
            redemptions++;
            sales.add(attributed);
            var code = codes.computeIfAbsent(promoCode, ignored -> new CodeTotal());
            code.redemptions++;
            code.sales.add(attributed);
        }

        CampaignSales view(UUID id) {
            return new CampaignSales(
                id,
                name,
                redemptions,
                sales.view(),
                codes
                    .entrySet()
                    .stream()
                    .map(entry -> new CodeSales(entry.getKey(), entry.getValue().redemptions, entry.getValue().sales.view()))
                    .toList()
            );
        }
    }

    private static final class CodeTotal {

        private long redemptions;
        private final SalesTotal sales = new SalesTotal();
    }
}
