package com.biletflow.biletflow.eventmanagement.persistence.layout.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "event_management_venue_layout",
    uniqueConstraints = @UniqueConstraint(name = "uk_event_management_venue_layout_name", columnNames = "name")
)
public class VenueLayoutJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "event_management_venue_layout_seat",
        joinColumns = @JoinColumn(name = "layout_id"),
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_event_management_layout_seat_id", columnNames = { "layout_id", "seat_id" }),
            @UniqueConstraint(
                name = "uk_event_management_layout_seat_location",
                columnNames = { "layout_id", "section_label", "row_label", "seat_number" }
            ),
        }
    )
    @OrderColumn(name = "seat_order")
    private List<VenueSeatJpaEmbeddable> seats = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected VenueLayoutJpaEntity() {}

    public VenueLayoutJpaEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<VenueSeatJpaEmbeddable> getSeats() {
        return seats;
    }

    public long getVersion() {
        return version;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void replaceSeats(List<VenueSeatJpaEmbeddable> seats) {
        this.seats.clear();
        this.seats.addAll(seats);
    }
}
