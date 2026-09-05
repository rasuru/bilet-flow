package com.biletflow.biletflow.eventmanagement.persistence.event;

import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.persistence.event.entity.RegistrationWindowModeJpa;
import com.biletflow.biletflow.eventmanagement.persistence.event.entity.SocialEventJpaEntity;
import com.biletflow.biletflow.eventmanagement.persistence.event.entity.StaffAssignmentJpaEmbeddable;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SocialEventPersistenceMapper {

    public SocialEvent toDomain(SocialEventJpaEntity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");

        RegistrationWindow registrationWindow = toDomainRegistrationWindow(entity);

        Venue venue = toDomainVenue(entity);

        List<StaffAssignment> staffAssignments = entity
            .getStaffAssignments()
            .stream()
            .map(assignment -> new StaffAssignment(assignment.getUserId(), assignment.getRole(), assignment.getAssignedAt()))
            .toList();

        return SocialEvent.rehydrate(
            new SocialEventId(entity.getId()),
            entity.getOrganizerId(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getCategory(),
            entity.getImageUrl(),
            entity.getStatus(),
            entity.getVisibility(),
            new EventDateRange(entity.getEventStartAt(), entity.getEventEndAt()),
            registrationWindow,
            venue,
            staffAssignments
        );
    }

    public SocialEventJpaEntity toNewEntity(SocialEvent domain) {
        Objects.requireNonNull(domain, "domain cannot be null");

        SocialEventJpaEntity entity = new SocialEventJpaEntity(domain.getId().value(), domain.getOrganizerId());

        updateEntity(domain, entity);
        return entity;
    }

    public void updateEntity(SocialEvent domain, SocialEventJpaEntity entity) {
        Objects.requireNonNull(domain, "domain cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");

        if (!entity.getId().equals(domain.getId().value())) {
            throw new IllegalArgumentException("Cannot map SocialEvent onto entity with another id");
        }

        if (!entity.getOrganizerId().equals(domain.getOrganizerId())) {
            throw new IllegalArgumentException("OrganizerId is immutable and cannot be changed");
        }

        entity.setCoreState(
            domain.getTitle(),
            domain.getDescription(),
            domain.getCategory(),
            domain.getImageUrl(),
            domain.getStatus(),
            domain.getVisibility(),
            domain.getDateRange().startAt(),
            domain.getDateRange().endAt()
        );

        mapRegistrationWindow(domain.getRegistrationWindow(), entity);

        mapVenue(domain, entity);

        entity.replaceStaffAssignments(
            domain
                .getStaffAssignments()
                .stream()
                .map(assignment -> new StaffAssignmentJpaEmbeddable(assignment.userId(), assignment.role(), assignment.assignedAt()))
                .toList()
        );
    }

    private void mapRegistrationWindow(RegistrationWindow window, SocialEventJpaEntity entity) {
        switch (window) {
            case RegistrationWindow.AlwaysOpen ignored -> entity.setRegistrationWindow(RegistrationWindowModeJpa.ALWAYS_OPEN, null, null);
            case RegistrationWindow.OpensAt opensAt -> entity.setRegistrationWindow(
                RegistrationWindowModeJpa.OPENS_AT,
                opensAt.start(),
                null
            );
            case RegistrationWindow.Bounded bounded -> entity.setRegistrationWindow(
                RegistrationWindowModeJpa.BOUNDED,
                bounded.start(),
                bounded.end()
            );
        }
    }

    private RegistrationWindow toDomainRegistrationWindow(SocialEventJpaEntity entity) {
        return switch (entity.getRegistrationWindowMode()) {
            case ALWAYS_OPEN -> new RegistrationWindow.AlwaysOpen();
            case OPENS_AT -> new RegistrationWindow.OpensAt(
                Objects.requireNonNull(entity.getRegistrationStartAt(), "registrationStartAt missing for OPENS_AT")
            );
            case BOUNDED -> new RegistrationWindow.Bounded(
                Objects.requireNonNull(entity.getRegistrationStartAt(), "registrationStartAt missing for BOUNDED"),
                Objects.requireNonNull(entity.getRegistrationEndAt(), "registrationEndAt missing for BOUNDED")
            );
        };
    }

    private void mapVenue(SocialEvent domain, SocialEventJpaEntity entity) {
        if (domain.getVenue().isEmpty()) {
            entity.clearVenue();
            return;
        }

        Venue venue = domain.getVenue().orElseThrow();

        switch (venue.getSeatingConfig()) {
            case Venue.GeneralAdmission ignored -> entity.setGeneralAdmissionVenue(
                venue.getName(),
                venue.getAddress(),
                venue.getTotalCapacity()
            );
            case Venue.AssignedSeating assigned -> entity.setAssignedSeatingVenue(
                venue.getName(),
                venue.getAddress(),
                venue.getTotalCapacity(),
                assigned.layoutId().value()
            );
        }
    }

    private Venue toDomainVenue(SocialEventJpaEntity entity) {
        if (entity.getSeatingMode() == null) {
            return null;
        }

        String name = Objects.requireNonNull(entity.getVenueName(), "venueName cannot be null when seating mode is set");

        String address = Objects.requireNonNull(entity.getVenueAddress(), "venueAddress cannot be null when seating mode is set");

        int capacity = Objects.requireNonNull(entity.getVenueTotalCapacity(), "venueTotalCapacity cannot be null when seating mode is set");

        return switch (entity.getSeatingMode()) {
            case GENERAL_ADMISSION -> Venue.createGeneralAdmission(name, address, capacity);
            case ASSIGNED_SEATING -> Venue.createAssignedSeating(
                name,
                address,
                capacity,
                new VenueLayoutId(Objects.requireNonNull(entity.getVenueLayoutId(), "venueLayoutId cannot be null for assigned seating"))
            );
        };
    }
}
