package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory;

import com.biletflow.biletflow.ticketinventory.domain.common.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHold;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryCounters;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservation;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryReservationId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.entity.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EventInventoryPersistenceMapper {

    public EventInventory toDomain(EventInventoryJpaEntity entity) {
        InventoryMode mode = switch (entity.getInventoryMode()) {
            case GENERAL_ADMISSION -> toGeneralAdmission(entity);
            case ASSIGNED_SEATING -> toAssignedSeating(entity);
        };

        return new EventInventory(
            new EventInventoryId(entity.getId()),
            entity.getEventId(),
            new HoldExpiry(Duration.ofMillis(entity.getHoldDurationMillis())),
            mode
        );
    }

    public EventInventoryJpaEntity toNewEntity(EventInventory domain) {
        EventInventoryJpaEntity entity = new EventInventoryJpaEntity(
            domain.getId().value(),
            domain.getEventId(),
            domain.getHoldExpiry().duration().toMillis(),
            toJpaMode(domain.getMode())
        );

        updateEntity(domain, entity);
        return entity;
    }

    public void updateEntity(EventInventory domain, EventInventoryJpaEntity entity) {
        if (!entity.getId().equals(domain.getId().value())) {
            throw new IllegalArgumentException("Cannot map onto a different EventInventory id");
        }

        if (!entity.getEventId().equals(domain.getEventId())) {
            throw new IllegalArgumentException("EventInventory eventId cannot change");
        }

        if (entity.getInventoryMode() != toJpaMode(domain.getMode())) {
            throw new IllegalArgumentException("EventInventory mode cannot change");
        }

        if (domain.getMode() instanceof GeneralAdmissionInventory ga) {
            entity.replaceState(
                domain.getHoldExpiry().duration().toMillis(),
                ga
                    .getCounters()
                    .entrySet()
                    .stream()
                    .map(entry ->
                        new GaInventoryCounterJpaEmbeddable(
                            entry.getKey().value(),
                            entry.getValue().totalCapacity(),
                            entry.getValue().reserved(),
                            entry.getValue().sold()
                        )
                    )
                    .toList(),
                ga.getReservations().stream().map(this::toEmbeddable).toList(),
                List.of(),
                List.of()
            );

            return;
        }

        AssignedSeatingInventory assigned = (AssignedSeatingInventory) domain.getMode();

        entity.replaceState(
            domain.getHoldExpiry().duration().toMillis(),
            List.of(),
            List.of(),
            assigned
                .getSeatPriceCategories()
                .entrySet()
                .stream()
                .map(entry -> new AssignedSeatJpaEmbeddable(entry.getKey().value(), entry.getValue()))
                .toList(),
            assigned.getHolds().stream().map(this::toEmbeddable).toList()
        );
    }

    private GeneralAdmissionInventory toGeneralAdmission(EventInventoryJpaEntity entity) {
        Map<TicketTypeId, InventoryCounters> counters = entity
            .getGaCounters()
            .stream()
            .collect(
                Collectors.toMap(
                    counter -> new TicketTypeId(counter.getTicketTypeId()),
                    counter -> new InventoryCounters(counter.getTotalCapacity(), counter.getReserved(), counter.getSold())
                )
            );

        List<InventoryReservation> reservations = entity
            .getGaReservations()
            .stream()
            .map(reservation ->
                new InventoryReservation(
                    new InventoryReservationId(reservation.getId()),
                    new TicketTypeId(reservation.getTicketTypeId()),
                    reservation.getQuantity(),
                    new SessionId(reservation.getSessionId()),
                    reservation.getOrderId() == null ? null : new OrderId(reservation.getOrderId()),
                    reservation.getStatus(),
                    reservation.getExpiresAt()
                )
            )
            .toList();

        return new GeneralAdmissionInventory(counters, reservations);
    }

    private AssignedSeatingInventory toAssignedSeating(EventInventoryJpaEntity entity) {
        Map<SeatId, String> seats = entity
            .getAssignedSeats()
            .stream()
            .collect(Collectors.toMap(seat -> new SeatId(seat.getSeatId()), AssignedSeatJpaEmbeddable::getPriceCategory));

        List<SeatHold> holds = entity
            .getSeatHolds()
            .stream()
            .map(hold ->
                new SeatHold(
                    new SeatHoldId(hold.getId()),
                    new TicketTypeId(hold.getTicketTypeId()),
                    new SeatId(hold.getSeatId()),
                    new SessionId(hold.getSessionId()),
                    hold.getOrderId() == null ? null : new OrderId(hold.getOrderId()),
                    hold.getStatus(),
                    hold.getExpiresAt()
                )
            )
            .toList();

        return new AssignedSeatingInventory(seats, holds);
    }

    private InventoryReservationJpaEmbeddable toEmbeddable(InventoryReservation reservation) {
        return new InventoryReservationJpaEmbeddable(
            reservation.getId().value(),
            reservation.getTicketTypeId().value(),
            reservation.getQuantity(),
            reservation.getSessionId().value(),
            reservation.getOrderId() == null ? null : reservation.getOrderId().value(),
            reservation.getStatus(),
            reservation.getExpiresAt()
        );
    }

    private SeatHoldJpaEmbeddable toEmbeddable(SeatHold hold) {
        return new SeatHoldJpaEmbeddable(
            hold.getId().value(),
            hold.getTicketTypeId().value(),
            hold.getSeatId().value(),
            hold.getSessionId().value(),
            hold.getOrderId() == null ? null : hold.getOrderId().value(),
            hold.getStatus(),
            hold.getExpiresAt()
        );
    }

    private InventoryModeJpa toJpaMode(InventoryMode mode) {
        if (mode instanceof GeneralAdmissionInventory) {
            return InventoryModeJpa.GENERAL_ADMISSION;
        }

        if (mode instanceof AssignedSeatingInventory) {
            return InventoryModeJpa.ASSIGNED_SEATING;
        }

        throw new IllegalArgumentException("Unsupported InventoryMode: " + mode.getClass());
    }
}
