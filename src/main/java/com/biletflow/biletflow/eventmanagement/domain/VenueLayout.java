package com.biletflow.biletflow.eventmanagement.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate root for predefined seating-layout reference data.
 */
public class VenueLayout {

    private final VenueLayoutId id;
    private final String name;
    private final List<VenueSection> sections;

    public VenueLayout(VenueLayoutId id, String name, List<VenueSection> sections) {
        this.id = Objects.requireNonNull(id, "VenueLayoutId cannot be null");

        Objects.requireNonNull(name, "Layout name cannot be null");
        String normalizedName = name.trim();

        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Venue layout name cannot be blank");
        }

        Objects.requireNonNull(sections, "Sections cannot be null");
        List<VenueSection> immutableSections = List.copyOf(sections);

        if (immutableSections.isEmpty()) {
            throw new IllegalArgumentException("Venue layout must contain at least one section");
        }

        this.name = normalizedName;
        this.sections = immutableSections;

        validateUniqueSeats();
    }

    public static VenueLayout create(String name, List<VenueSection> sections) {
        return new VenueLayout(VenueLayoutId.generate(), name, sections);
    }

    private void validateUniqueSeats() {
        Set<SeatId> ids = new HashSet<>();
        Set<SeatLocation> locations = new HashSet<>();

        for (VenueSeat seat : getSeats()) {
            if (!ids.add(seat.id())) {
                throw new IllegalArgumentException("Duplicate SeatId in layout: " + seat.id());
            }

            if (!locations.add(seat.location())) {
                throw new IllegalArgumentException("Duplicate seat location in layout: " + seat.location().toLabel());
            }
        }
    }

    public List<VenueSeat> getSeats() {
        return sections
            .stream()
            .flatMap(section -> section.rows().stream())
            .flatMap(row -> row.seats().stream())
            .toList();
    }

    public Set<SeatId> getSeatIds() {
        return getSeats().stream().map(VenueSeat::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public VenueLayoutId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<VenueSection> getSections() {
        return sections;
    }
}
