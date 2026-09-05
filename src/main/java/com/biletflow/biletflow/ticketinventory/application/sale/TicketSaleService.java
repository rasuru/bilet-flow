package com.biletflow.biletflow.ticketinventory.application.sale;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.*;
import com.biletflow.biletflow.ticketinventory.application.sale.command.CompleteSaleCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.IssueTicketItem;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHold;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservation;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservationId;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketSaleService {

    private final EventInventoryRepository inventoryRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final Clock clock;

    public TicketSaleService(
        EventInventoryRepository inventoryRepository,
        TicketRepository ticketRepository,
        TicketTypeRepository ticketTypeRepository,
        Clock clock
    ) {
        this.inventoryRepository = Objects.requireNonNull(inventoryRepository);
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
        this.ticketTypeRepository = Objects.requireNonNull(ticketTypeRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public List<TicketView> complete(CompleteSaleCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        OrderId orderId = new OrderId(command.orderId());

        List<Ticket> existing = ticketRepository.findAllByOrderId(orderId);

        if (!existing.isEmpty()) {
            ensureRetryMatchesExisting(command, existing);

            return existing.stream().map(this::toView).toList();
        }

        EventInventory inventory = inventoryRepository
            .findByEventId(command.eventId())
            .orElseThrow(() -> new EntityNotFoundException("EventInventory not found for event: " + command.eventId()));

        validateHoldItemsMatch(inventory, command.holds(), command.items());

        validateTicketTypes(command.eventId(), command.items());

        Instant now = clock.instant();

        for (HoldReference reference : command.holds()) {
            if (reference instanceof GaReservationReference ga) {
                inventory.confirmGAReservation(new InventoryReservationId(ga.reservationId()), orderId, now);
            } else if (reference instanceof SeatHoldReference seat) {
                inventory.confirmSeatHold(new SeatHoldId(seat.holdId()), orderId, now);
            } else {
                throw new IllegalStateException("Unsupported hold reference: " + reference.getClass().getName());
            }
        }

        List<Ticket> tickets = command
            .items()
            .stream()
            .map(item ->
                Ticket.issue(
                    new TicketTypeId(item.ticketTypeId()),
                    command.eventId(),
                    orderId,
                    new AttendeeEmail(item.attendeeEmail()),
                    item.ownerUserId(),
                    item.seatId() == null ? null : new SeatId(item.seatId()),
                    now
                )
            )
            .toList();

        inventoryRepository.save(inventory);

        return ticketRepository.saveAll(tickets).stream().map(this::toView).toList();
    }

    private void validateHoldItemsMatch(EventInventory inventory, List<HoldReference> holds, List<IssueTicketItem> items) {
        if (inventory.getMode() instanceof GeneralAdmissionInventory ga) {
            Map<TicketTypeId, Integer> heldQuantities = new HashMap<>();

            for (HoldReference reference : holds) {
                if (!(reference instanceof GaReservationReference gaRef)) {
                    throw new IllegalStateException("Assigned-seat hold supplied for general-admission inventory");
                }

                InventoryReservation reservation = ga
                    .getReservations()
                    .stream()
                    .filter(candidate -> candidate.getId().value().equals(gaRef.reservationId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("GA reservation does not belong to this inventory"));

                heldQuantities.merge(reservation.getTicketTypeId(), reservation.getQuantity(), Integer::sum);
            }

            Map<TicketTypeId, Integer> itemQuantities = new HashMap<>();

            for (IssueTicketItem item : items) {
                if (item.seatId() != null) {
                    throw new IllegalStateException("General-admission ticket item cannot contain a seat");
                }

                itemQuantities.merge(new TicketTypeId(item.ticketTypeId()), 1, Integer::sum);
            }

            if (!heldQuantities.equals(itemQuantities)) {
                throw new IllegalStateException("Ticket items do not match reserved GA inventory");
            }

            return;
        }

        AssignedSeatingInventory assigned = (AssignedSeatingInventory) inventory.getMode();

        Set<SeatFingerprint> heldSeats = holds
            .stream()
            .map(reference -> {
                if (!(reference instanceof SeatHoldReference seatRef)) {
                    throw new IllegalStateException("GA reservation supplied for assigned-seating inventory");
                }

                SeatHold hold = assigned
                    .getHolds()
                    .stream()
                    .filter(candidate -> candidate.getId().value().equals(seatRef.holdId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Seat hold does not belong to this inventory"));

                return new SeatFingerprint(hold.getTicketTypeId().value(), hold.getSeatId().value());
            })
            .collect(Collectors.toUnmodifiableSet());

        Set<SeatFingerprint> requestedSeats = items
            .stream()
            .map(item -> {
                if (item.seatId() == null) {
                    throw new IllegalStateException("Assigned-seating ticket item requires a seat");
                }

                return new SeatFingerprint(item.ticketTypeId(), item.seatId());
            })
            .collect(Collectors.toUnmodifiableSet());

        if (heldSeats.size() != items.size() || !heldSeats.equals(requestedSeats)) {
            throw new IllegalStateException("Ticket items do not match held assigned seats");
        }
    }

    private void validateTicketTypes(UUID eventId, List<IssueTicketItem> items) {
        for (IssueTicketItem item : items) {
            TicketType ticketType = ticketTypeRepository
                .findById(new TicketTypeId(item.ticketTypeId()))
                .orElseThrow(() -> new EntityNotFoundException("TicketType not found: " + item.ticketTypeId()));

            if (!ticketType.getEventId().equals(eventId)) {
                throw new IllegalStateException("Ticket type does not belong to sale event");
            }
        }
    }

    private void ensureRetryMatchesExisting(CompleteSaleCommand command, List<Ticket> existing) {
        if (existing.size() != command.items().size()) {
            throw new IllegalStateException("Order already has a different number of issued tickets");
        }

        Map<IssueFingerprint, Long> requested = command
            .items()
            .stream()
            .map(item ->
                new IssueFingerprint(
                    item.ticketTypeId(),
                    new AttendeeEmail(item.attendeeEmail()).value(),
                    item.ownerUserId(),
                    item.seatId()
                )
            )
            .collect(Collectors.groupingBy(fingerprint -> fingerprint, Collectors.counting()));

        Map<IssueFingerprint, Long> stored = existing
            .stream()
            .map(ticket ->
                new IssueFingerprint(
                    ticket.getTicketTypeId().value(),
                    ticket.getAttendeeEmail().value(),
                    ticket.getOwnerUserId(),
                    ticket.getSeatId() == null ? null : ticket.getSeatId().value()
                )
            )
            .collect(Collectors.groupingBy(fingerprint -> fingerprint, Collectors.counting()));

        if (!requested.equals(stored)) {
            throw new IllegalStateException("Order already has tickets that do not match this sale retry");
        }
    }

    private TicketView toView(Ticket ticket) {
        return new TicketView(
            ticket.getId().value(),
            ticket.getTicketTypeId().value(),
            ticket.getEventId(),
            ticket.getOrderId().value(),
            ticket.getAttendeeEmail().value(),
            ticket.getOwnerUserId(),
            ticket.getSeatId() == null ? null : ticket.getSeatId().value(),
            ticket.getTicketCode().value(),
            ticket.getStatus(),
            ticket.getIssuedAt()
        );
    }

    private record SeatFingerprint(UUID ticketTypeId, UUID seatId) {}

    private record IssueFingerprint(UUID ticketTypeId, String attendeeEmail, Long ownerUserId, UUID seatId) {}
}
