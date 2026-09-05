package com.biletflow.biletflow.ticketinventory.application.eventinventory;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.ticketinventory.application.common.InventoryKind;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.*;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.result.HoldInventoryResult;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.view.*;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHold;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryCounters;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservation;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservationId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventInventoryService {

    private final EventInventoryRepository inventoryRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final Clock clock;

    public EventInventoryService(EventInventoryRepository inventoryRepository, TicketTypeRepository ticketTypeRepository, Clock clock) {
        this.inventoryRepository = Objects.requireNonNull(inventoryRepository);
        this.ticketTypeRepository = Objects.requireNonNull(ticketTypeRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public HoldInventoryResult hold(HoldInventoryCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        EventInventory inventory = requireInventory(command.eventId());

        TicketType ticketType = requireTicketType(command.ticketTypeId());

        ensureTicketTypeBelongsToEvent(ticketType, command.eventId());

        Instant now = clock.instant();

        if (!ticketType.isPurchasableAt(now)) {
            throw new IllegalStateException("Ticket type is not currently purchasable");
        }

        SessionId sessionId = new SessionId(command.sessionId());

        HoldInventoryResult result;

        if (command.selection() instanceof GeneralAdmissionSelection gaSelection) {
            ensureGeneralAdmission(inventory);

            if (ticketType.isAssignedSeatingType()) {
                throw new IllegalStateException("Assigned-seating ticket type cannot reserve general-admission inventory");
            }

            if (gaSelection.quantity() > ticketType.getMaxPerOrder()) {
                throw new IllegalStateException("Requested quantity exceeds ticket type per-order limit");
            }

            InventoryReservation reservation = inventory.reserveGA(ticketType.getId(), gaSelection.quantity(), sessionId, now);

            result = new HoldInventoryResult(List.of(new GaReservationReference(reservation.getId().value())), reservation.getExpiresAt());
        } else if (command.selection() instanceof AssignedSeatsSelection seatSelection) {
            ensureAssignedSeating(inventory);

            AssignedSeatingInventory assigned = (AssignedSeatingInventory) inventory.getMode();

            if (seatSelection.seatIds().size() > ticketType.getMaxPerOrder()) {
                throw new IllegalStateException("Selected seat count exceeds ticket type per-order limit");
            }

            List<SeatHold> holds = new ArrayList<>();

            for (UUID rawSeatId : seatSelection.seatIds()) {
                SeatId seatId = new SeatId(rawSeatId);

                String seatCategory = assigned.getPriceCategory(seatId);

                if (!Objects.equals(ticketType.getPriceCategory(), seatCategory)) {
                    throw new IllegalStateException("Seat price category does not match ticket type");
                }

                holds.add(inventory.holdSeat(ticketType.getId(), seatId, sessionId, now));
            }

            Instant expiresAt = holds.stream().map(SeatHold::getExpiresAt).min(Comparator.naturalOrder()).orElseThrow();

            result = new HoldInventoryResult(
                holds
                    .stream()
                    .map(hold -> (HoldReference) new SeatHoldReference(hold.getId().value()))
                    .toList(),
                expiresAt
            );
        } else {
            throw new IllegalStateException("Unsupported hold selection: " + command.selection().getClass().getName());
        }

        inventoryRepository.save(inventory);
        return result;
    }

    @Transactional
    public void releaseHold(ReleaseHoldCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        EventInventory inventory = requireInventory(command.eventId());

        SessionId sessionId = new SessionId(command.sessionId());

        for (HoldReference reference : command.holds()) {
            if (reference instanceof GaReservationReference gaReference) {
                inventory.releaseGAReservation(new InventoryReservationId(gaReference.reservationId()), sessionId);
            } else if (reference instanceof SeatHoldReference seatReference) {
                inventory.releaseSeatHold(new SeatHoldId(seatReference.holdId()), sessionId);
            } else {
                throw new IllegalStateException("Unsupported hold reference: " + reference.getClass().getName());
            }
        }

        inventoryRepository.save(inventory);
    }

    @Transactional
    public void cleanupExpiredHolds(CleanupExpiredHoldsCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        EventInventory inventory = requireInventory(command.eventId());

        inventory.cleanUpExpiredHolds(clock.instant());

        inventoryRepository.save(inventory);
    }

    public EventInventoryView getInventory(UUID eventId) {
        EventInventory inventory = requireInventory(eventId);

        if (inventory.getMode() instanceof GeneralAdmissionInventory ga) {
            List<GaAvailabilityView> availability = ga
                .getCounters()
                .entrySet()
                .stream()
                .map(entry -> toView(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(view -> view.ticketTypeId().toString()))
                .toList();

            return new EventInventoryView(
                inventory.getId().value(),
                inventory.getEventId(),
                InventoryKind.GENERAL_ADMISSION,
                availability,
                List.of()
            );
        }

        AssignedSeatingInventory assigned = (AssignedSeatingInventory) inventory.getMode();

        Map<SeatId, SeatAvailabilityStatus> statusBySeat = new HashMap<>();

        for (SeatId seatId : assigned.getTotalSeats()) {
            statusBySeat.put(seatId, SeatAvailabilityStatus.AVAILABLE);
        }

        for (SeatHold hold : assigned.getHolds()) {
            if (hold.getStatus() == SeatHoldStatus.HELD) {
                statusBySeat.put(hold.getSeatId(), SeatAvailabilityStatus.HELD);
            } else if (hold.getStatus() == SeatHoldStatus.SOLD) {
                statusBySeat.put(hold.getSeatId(), SeatAvailabilityStatus.SOLD);
            }
        }

        List<SeatAvailabilityView> seats = statusBySeat
            .entrySet()
            .stream()
            .map(entry -> new SeatAvailabilityView(entry.getKey().value(), entry.getValue()))
            .sorted(Comparator.comparing(view -> view.seatId().toString()))
            .toList();

        return new EventInventoryView(inventory.getId().value(), inventory.getEventId(), InventoryKind.ASSIGNED_SEATING, List.of(), seats);
    }

    public List<GaAvailabilityView> getAvailability(UUID eventId) {
        EventInventory inventory = requireInventory(eventId);

        ensureGeneralAdmission(inventory);

        GeneralAdmissionInventory ga = (GeneralAdmissionInventory) inventory.getMode();

        return ga
            .getCounters()
            .entrySet()
            .stream()
            .map(entry -> toView(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(view -> view.ticketTypeId().toString()))
            .toList();
    }

    public List<SeatAvailabilityView> getSeatMap(UUID eventId) {
        EventInventoryView inventory = getInventory(eventId);

        if (inventory.inventoryKind() != InventoryKind.ASSIGNED_SEATING) {
            throw new IllegalStateException("Seat availability is only defined for assigned-seating events");
        }

        return inventory.seatAvailability();
    }

    private EventInventory requireInventory(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");

        return inventoryRepository
            .findByEventId(eventId)
            .orElseThrow(() -> new EntityNotFoundException("EventInventory not found for event: " + eventId));
    }

    private TicketType requireTicketType(UUID ticketTypeId) {
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");

        return ticketTypeRepository
            .findById(new TicketTypeId(ticketTypeId))
            .orElseThrow(() -> new EntityNotFoundException("TicketType not found: " + ticketTypeId));
    }

    private void ensureTicketTypeBelongsToEvent(TicketType ticketType, UUID eventId) {
        if (!ticketType.getEventId().equals(eventId)) {
            throw new IllegalStateException("Ticket type does not belong to event");
        }
    }

    private void ensureGeneralAdmission(EventInventory inventory) {
        if (!(inventory.getMode() instanceof GeneralAdmissionInventory)) {
            throw new IllegalStateException("Operation requires general-admission inventory");
        }
    }

    private void ensureAssignedSeating(EventInventory inventory) {
        if (!(inventory.getMode() instanceof AssignedSeatingInventory)) {
            throw new IllegalStateException("Operation requires assigned-seating inventory");
        }
    }

    private GaAvailabilityView toView(TicketTypeId ticketTypeId, InventoryCounters counters) {
        return new GaAvailabilityView(
            ticketTypeId.value(),
            counters.totalCapacity(),
            counters.reserved(),
            counters.sold(),
            counters.available()
        );
    }
}
