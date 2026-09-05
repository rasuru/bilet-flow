package com.biletflow.biletflow.ticketinventory.persistence.eventinventory.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "ticket_inventory_event_inventory",
    uniqueConstraints = @UniqueConstraint(name = "uk_ticket_inventory_event_inventory_event", columnNames = "event_id")
)
public class EventInventoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "hold_duration_millis", nullable = false)
    private long holdDurationMillis;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_mode", nullable = false, updatable = false, length = 32)
    private InventoryModeJpa inventoryMode;

    /**
     * Infrastructure-only optimistic-lock state. It is intentionally absent
     * from the domain aggregate.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "ticket_inventory_ga_counter",
        joinColumns = @JoinColumn(name = "event_inventory_id"),
        uniqueConstraints = @UniqueConstraint(
            name = "uk_ticket_inventory_ga_counter_type",
            columnNames = { "event_inventory_id", "ticket_type_id" }
        )
    )
    private List<GaInventoryCounterJpaEmbeddable> gaCounters = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "ticket_inventory_ga_reservation",
        joinColumns = @JoinColumn(name = "event_inventory_id"),
        uniqueConstraints = @UniqueConstraint(
            name = "uk_ticket_inventory_ga_reservation_id",
            columnNames = { "event_inventory_id", "reservation_id" }
        )
    )
    private List<InventoryReservationJpaEmbeddable> gaReservations = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "ticket_inventory_assigned_seat",
        joinColumns = @JoinColumn(name = "event_inventory_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_ticket_inventory_assigned_seat", columnNames = { "event_inventory_id", "seat_id" })
    )
    private List<AssignedSeatJpaEmbeddable> assignedSeats = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "ticket_inventory_seat_hold",
        joinColumns = @JoinColumn(name = "event_inventory_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_ticket_inventory_seat_hold_id", columnNames = { "event_inventory_id", "hold_id" })
    )
    private List<SeatHoldJpaEmbeddable> seatHolds = new ArrayList<>();

    protected EventInventoryJpaEntity() {}

    public EventInventoryJpaEntity(UUID id, UUID eventId, long holdDurationMillis, InventoryModeJpa inventoryMode) {
        this.id = id;
        this.eventId = eventId;
        this.holdDurationMillis = holdDurationMillis;
        this.inventoryMode = inventoryMode;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getHoldDurationMillis() {
        return holdDurationMillis;
    }

    public InventoryModeJpa getInventoryMode() {
        return inventoryMode;
    }

    public long getVersion() {
        return version;
    }

    public List<GaInventoryCounterJpaEmbeddable> getGaCounters() {
        return gaCounters;
    }

    public List<InventoryReservationJpaEmbeddable> getGaReservations() {
        return gaReservations;
    }

    public List<AssignedSeatJpaEmbeddable> getAssignedSeats() {
        return assignedSeats;
    }

    public List<SeatHoldJpaEmbeddable> getSeatHolds() {
        return seatHolds;
    }

    public void replaceState(
        long holdDurationMillis,
        List<GaInventoryCounterJpaEmbeddable> gaCounters,
        List<InventoryReservationJpaEmbeddable> gaReservations,
        List<AssignedSeatJpaEmbeddable> assignedSeats,
        List<SeatHoldJpaEmbeddable> seatHolds
    ) {
        this.holdDurationMillis = holdDurationMillis;

        this.gaCounters.clear();
        this.gaCounters.addAll(gaCounters);

        this.gaReservations.clear();
        this.gaReservations.addAll(gaReservations);

        this.assignedSeats.clear();
        this.assignedSeats.addAll(assignedSeats);

        this.seatHolds.clear();
        this.seatHolds.addAll(seatHolds);
    }
}
