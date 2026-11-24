package com.biletflow.biletflow.ticketinventory.persistence;

import com.biletflow.biletflow.ticketinventory.domain.eventinventory.AssignedSeatingInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.GeneralAdmissionInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import com.biletflow.biletflow.ticketinventory.ohs.TicketAnalyticsOhs;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTicketAnalytics implements TicketAnalyticsOhs {

    private final EventInventoryRepository inventories;
    private final TicketTypeRepository types;
    private final EntityManager entities;
    private final Clock clock;

    public JpaTicketAnalytics(EventInventoryRepository inventories, TicketTypeRepository types, EntityManager entities, Clock clock) {
        this.inventories = inventories;
        this.types = types;
        this.entities = entities;
        this.clock = clock;
    }

    @Override
    public Inventory inventory(UUID eventId, UUID ticketTypeId) {
        var eventTypes = types.findAllByEventId(eventId);
        var selected = eventTypes
            .stream()
            .filter(type -> type.getId().value().equals(ticketTypeId))
            .findFirst();
        if (ticketTypeId != null && selected.isEmpty()) {
            throw new IllegalArgumentException("The ticket type does not belong to this event.");
        }
        var names = eventTypes
            .stream()
            .map(type -> new TicketType(type.getId().value(), type.getName()))
            .sorted(Comparator.comparing(TicketType::name).thenComparing(TicketType::id))
            .toList();

        var snapshot = inventories.findByEventId(eventId).orElse(null);
        if (snapshot == null) return new Inventory(names, 0, 0, 0, 0, false);
        snapshot.cleanUpExpiredHolds(clock.instant());

        long capacity = 0;
        long sold = 0;
        long held = 0;
        boolean sharedCapacity = false;
        if (snapshot.getMode() instanceof GeneralAdmissionInventory ga) {
            for (var entry : ga.getCounters().entrySet()) {
                if (ticketTypeId != null && !ticketTypeId.equals(entry.getKey().value())) continue;
                var counters = entry.getValue();
                capacity += counters.totalCapacity();
                sold += counters.sold();
                held += counters.reserved();
            }
        } else if (snapshot.getMode() instanceof AssignedSeatingInventory assigned) {
            // Several ticket types can sell seats from the same category.
            var category = selected.map(type -> type.getPriceCategory()).orElse(null);
            var seats = assigned.getSeatPriceCategories();
            capacity = seats
                .values()
                .stream()
                .filter(value -> ticketTypeId == null || Objects.equals(category, value))
                .count();
            sharedCapacity =
                ticketTypeId != null &&
                eventTypes
                    .stream()
                    .filter(type -> Objects.equals(category, type.getPriceCategory()))
                    .count() > 1;
            for (var hold : assigned.getHolds()) {
                if (ticketTypeId != null && !Objects.equals(category, seats.get(hold.getSeatId()))) continue;
                if (hold.getStatus() == SeatHoldStatus.SOLD) sold++;
                if (hold.getStatus() == SeatHoldStatus.HELD) held++;
            }
        }
        return new Inventory(names, capacity, sold, capacity - sold - held, held, sharedCapacity);
    }

    @Override
    public List<Attendance> attendance(UUID eventId, UUID ticketTypeId) {
        return entities
            .createQuery(
                """
                select t.orderId, count(t), sum(case when t.status = :checkedIn then 1L else 0L end)
                from TicketJpaEntity t
                where t.eventId = :eventId and t.status in (:valid, :checkedIn)
                  and (:allTypes = true or t.ticketTypeId = :ticketTypeId)
                group by t.orderId
                """,
                Attendance.class
            )
            .setParameter("eventId", eventId)
            .setParameter("valid", TicketStatus.VALID)
            .setParameter("checkedIn", TicketStatus.CHECKED_IN)
            .setParameter("allTypes", ticketTypeId == null)
            .setParameter("ticketTypeId", ticketTypeId)
            .getResultList();
    }
}
