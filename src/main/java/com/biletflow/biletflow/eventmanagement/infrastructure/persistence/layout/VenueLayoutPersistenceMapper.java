package com.biletflow.biletflow.eventmanagement.infrastructure.persistence.layout;

import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.infrastructure.persistence.layout.entity.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class VenueLayoutPersistenceMapper {

    public VenueLayout toDomain(VenueLayoutJpaEntity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");

        Map<String, LinkedHashMap<String, List<VenueSeat>>> grouped = new LinkedHashMap<>();

        for (VenueSeatJpaEmbeddable storedSeat : entity.getSeats()) {
            VenueSeat seat = new VenueSeat(
                new SeatId(storedSeat.getSeatId()),
                new SeatLocation(storedSeat.getSection(), storedSeat.getRow(), storedSeat.getSeatNumber()),
                storedSeat.isAccessible(),
                storedSeat.getPriceCategory()
            );

            grouped
                .computeIfAbsent(storedSeat.getSection(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(storedSeat.getRow(), ignored -> new ArrayList<>())
                .add(seat);
        }

        List<VenueSection> sections = grouped
            .entrySet()
            .stream()
            .map(sectionEntry ->
                new VenueSection(
                    sectionEntry.getKey(),
                    sectionEntry
                        .getValue()
                        .entrySet()
                        .stream()
                        .map(rowEntry -> new VenueRow(rowEntry.getKey(), rowEntry.getValue()))
                        .toList()
                )
            )
            .toList();

        return new VenueLayout(new VenueLayoutId(entity.getId()), entity.getName(), sections);
    }

    public VenueLayoutJpaEntity toNewEntity(VenueLayout domain) {
        VenueLayoutJpaEntity entity = new VenueLayoutJpaEntity(domain.getId().value());

        updateEntity(domain, entity);
        return entity;
    }

    public void updateEntity(VenueLayout domain, VenueLayoutJpaEntity entity) {
        Objects.requireNonNull(domain, "domain cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");

        if (!entity.getId().equals(domain.getId().value())) {
            throw new IllegalArgumentException("Cannot map VenueLayout onto entity with another id");
        }

        entity.setName(domain.getName());

        entity.replaceSeats(
            domain
                .getSeats()
                .stream()
                .map(seat ->
                    new VenueSeatJpaEmbeddable(
                        seat.id().value(),
                        seat.location().section(),
                        seat.location().row(),
                        seat.location().seatNumber(),
                        seat.accessible(),
                        seat.priceCategory()
                    )
                )
                .toList()
        );
    }
}
