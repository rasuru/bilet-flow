package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.EventInventoryService;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.*;
import com.biletflow.biletflow.ticketinventory.application.sale.TicketSaleService;
import com.biletflow.biletflow.ticketinventory.application.sale.command.CompleteSaleCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.IssueTicketItem;
import com.biletflow.biletflow.ticketinventory.application.tickettype.TicketTypeService;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultCheckoutInventoryOhs implements CheckoutInventoryOhs {

    private final EventInventoryService inventory;
    private final TicketSaleService sales;
    private final TicketTypeService types;
    private final TicketRepository tickets;
    private final EventInventoryRepository inventories;

    public DefaultCheckoutInventoryOhs(
        EventInventoryService inventory,
        TicketSaleService sales,
        TicketTypeService types,
        TicketRepository tickets,
        EventInventoryRepository inventories
    ) {
        this.inventory = inventory;
        this.sales = sales;
        this.types = types;
        this.tickets = tickets;
        this.inventories = inventories;
    }

    @Override
    public Reservation reserve(UUID eventId, UUID checkoutId, List<Selection> selections) {
        List<Line> lines = new ArrayList<>();
        List<Hold> holds = new ArrayList<>();
        Instant deadline = Instant.MAX;
        Boolean assigned = null;
        Set<UUID> selectedTypes = new HashSet<>();
        for (Selection s : selections) {
            if (!selectedTypes.add(s.ticketTypeId())) throw new IllegalArgumentException("Combine selections for each ticket type");
            var type = types.get(s.ticketTypeId());
            if (!type.eventId().equals(eventId)) throw new IllegalArgumentException("Ticket type belongs to a different event");
            if (s.attendeeEmails().isEmpty() || s.attendeeEmails().size() > 100) throw new IllegalArgumentException(
                "Invalid attendee count"
            );
            s.attendeeEmails().forEach(AttendeeEmail::new);
            boolean seatMode = !s.seats().isEmpty();
            if (assigned != null && assigned != seatMode) throw new IllegalArgumentException("Cannot mix seating modes");
            assigned = seatMode;
            if (
                seatMode && (s.seats().size() != s.attendeeEmails().size() || new HashSet<>(s.seats()).size() != s.seats().size())
            ) throw new IllegalArgumentException("Supply one distinct seat per attendee");
            HoldSelection choice = seatMode
                ? new AssignedSeatsSelection(new LinkedHashSet<>(s.seats()))
                : new GeneralAdmissionSelection(s.attendeeEmails().size());
            var held = inventory.hold(new HoldInventoryCommand(eventId, s.ticketTypeId(), checkoutId.toString(), choice));
            if (held.expiresAt().isBefore(deadline)) deadline = held.expiresAt();
            held.holds().forEach(h ->
                holds.add(
                    h instanceof SeatHoldReference seat
                        ? new Hold(seat.holdId(), true)
                        : new Hold(((GaReservationReference) h).reservationId(), false)
                )
            );
            for (int i = 0; i < s.attendeeEmails().size(); i++) lines.add(
                new Line(s.ticketTypeId(), seatMode ? s.seats().get(i) : null, type.price(), s.attendeeEmails().get(i))
            );
        }
        if (lines.isEmpty() || lines.size() > 100) throw new IllegalArgumentException("Checkout requires 1 to 100 tickets");
        return new Reservation(List.copyOf(lines), List.copyOf(holds), deadline, Boolean.TRUE.equals(assigned));
    }

    @Override
    public void release(UUID eventId, UUID checkoutId, List<Hold> holds) {
        inventory.releaseHold(new ReleaseHoldCommand(eventId, checkoutId.toString(), references(holds)));
    }

    @Override
    public List<IssuedTicket> issue(UUID eventId, UUID checkoutId, long ownerId, List<Line> lines, List<Hold> holds) {
        return sales
            .complete(
                new CompleteSaleCommand(
                    checkoutId,
                    eventId,
                    references(holds),
                    lines
                        .stream()
                        // A purchaser is not automatically the owner of tickets bought for other people. Claiming uses verified email.
                        .map(l -> new IssueTicketItem(l.ticketTypeId(), l.email(), null, l.seatId()))
                        .toList()
                )
            )
            .stream()
            .map(t -> new IssuedTicket(t.id(), t.ticketCode(), t.status().name(), t.ticketTypeId(), t.seatId(), t.attendeeEmail()))
            .toList();
    }

    @Override
    public List<IssuedTicket> tickets(UUID orderId) {
        return tickets
            .findAllByOrderId(new OrderId(orderId))
            .stream()
            .map(t ->
                new IssuedTicket(
                    t.getId().value(),
                    t.getTicketCode().value(),
                    t.getStatus().name(),
                    t.getTicketTypeId().value(),
                    t.getSeatId() == null ? null : t.getSeatId().value(),
                    t.getAttendeeEmail().value()
                )
            )
            .toList();
    }

    @Override
    public void prepareRefund(UUID eventId, UUID orderId) {
        List<Ticket> all = lockedTickets(eventId, orderId);
        if (all.isEmpty()) throw new IllegalStateException("Order has no tickets");
        all.forEach(Ticket::prepareRefund);
        all.forEach(tickets::save);
    }

    @Override
    public void completeRefund(UUID eventId, UUID orderId) {
        invalidate(eventId, orderId, true);
    }

    @Override
    public void cancelFreeOrder(UUID eventId, UUID orderId) {
        invalidate(eventId, orderId, false);
    }

    @Override
    public void failRefund(UUID eventId, UUID orderId) {
        lockedTickets(eventId, orderId).forEach(t -> {
            t.restoreAfterFailedRefund();
            tickets.save(t);
        });
    }

    private void invalidate(UUID eventId, UUID orderId, boolean refund) {
        var all = lockedTickets(eventId, orderId);
        if (all.isEmpty()) return;
        // Compensation before ticket issuance is valid.
        var stock = inventories.findByEventId(eventId).orElseThrow();
        for (Ticket ticket : all) {
            boolean changed = refund ? ticket.applyConfirmedRefund() : ticket.cancel();
            if (!changed) {
                tickets.save(ticket);
                continue;
            }
            if (ticket.getSeatId() == null) stock.reverseGASale(ticket.getTicketTypeId(), 1);
            else if (refund) stock.refundSoldSeat(ticket.getSeatId(), ticket.getOrderId());
            else stock.cancelSoldSeat(ticket.getSeatId(), ticket.getOrderId());
            tickets.save(ticket);
        }
        inventories.save(stock);
    }

    private List<Ticket> lockedTickets(UUID eventId, UUID orderId) {
        List<Ticket> all = tickets
            .findAllByOrderId(new OrderId(orderId))
            .stream()
            .sorted(Comparator.comparing(t -> t.getTicketCode().value()))
            .map(t -> tickets.findByTicketCodeForUpdate(t.getTicketCode()).orElseThrow())
            .toList();
        if (all.stream().anyMatch(t -> !t.getEventId().equals(eventId))) throw new IllegalStateException("Order event mismatch");
        return all;
    }

    private List<HoldReference> references(List<Hold> holds) {
        return holds
            .stream()
            .map(h ->
                h.assignedSeat() ? (HoldReference) new SeatHoldReference(h.referenceId()) : new GaReservationReference(h.referenceId())
            )
            .toList();
    }
}
