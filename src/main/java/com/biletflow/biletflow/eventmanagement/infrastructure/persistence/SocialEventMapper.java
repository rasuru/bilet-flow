package com.biletflow.biletflow.eventmanagement.infrastructure.persistence;

import com.biletflow.biletflow.eventmanagement.domain.*;
import com.biletflow.biletflow.eventmanagement.infrastructure.persistence.entity.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SocialEventMapper {

    public SocialEventEntity toEntity(SocialEvent domain) {
        SocialEventEntity entity = new SocialEventEntity();
        entity.setId(domain.getId().value());
        entity.setOrganizerId(domain.getOrganizerId());
        entity.setTitle(domain.getTitle());
        entity.setDescription(domain.getDescription());
        entity.setCategory(domain.getCategory());
        entity.setImageUrl(domain.getImageUrl());
        entity.setStatus(domain.getStatus().name());
        entity.setVisibility(domain.getVisibility().name());

        // Date Range
        entity.setStartAt(domain.getDateRange().startAt());
        entity.setEndAt(domain.getDateRange().endAt());

        // Registration Window ADT
        switch (domain.getRegistrationWindow()) {
            case RegistrationWindow.AlwaysOpen ao -> {
                entity.setRegWindowType("ALWAYS_OPEN");
            }
            case RegistrationWindow.OpensAt oa -> {
                entity.setRegWindowType("OPENS_AT");
                entity.setRegWindowStart(oa.start());
            }
            case RegistrationWindow.Bounded b -> {
                entity.setRegWindowType("BOUNDED");
                entity.setRegWindowStart(b.start());
                entity.setRegWindowEnd(b.end());
            }
        }

        // Venue
        domain.getVenue().ifPresent(venue -> {
            entity.setVenueName(venue.getName());
            entity.setVenueAddress(venue.getAddress());
            entity.setVenueCapacity(venue.getTotalCapacity());
            switch (venue.getSeatingConfig()) {
                case Venue.GeneralAdmission ga -> entity.setVenueSeatingType("GENERAL_ADMISSION");
                case Venue.AssignedSeating ass -> {
                    entity.setVenueSeatingType("ASSIGNED_SEATING");
                    entity.setVenueLayoutId(ass.layoutId());
                }
            }
        });

        // Ticket Types
        entity.setTicketTypes(domain.getTicketTypes().stream().map(this::toTicketTypeEntity).toList());

        // Staff Assignments
        entity.setStaffAssignments(
            domain
                .getStaffAssignments()
                .stream()
                .map(sa -> new StaffAssignmentEmbeddable(sa.userId(), sa.role().name(), sa.assignedAt()))
                .toList()
        );

        return entity;
    }

    private TicketTypeEntity toTicketTypeEntity(TicketType domain) {
        TicketTypeEntity entity = new TicketTypeEntity();
        entity.setId(domain.getId().value());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setTotalQuantity(domain.getTotalQuantity());
        entity.setSalesStart(domain.getSalesWindow().start());
        entity.setSalesEnd(domain.getSalesWindow().end());
        entity.setMaxPerOrder(domain.getMaxPerOrder());
        entity.setHidden(domain.isHidden());

        switch (domain.getPricing()) {
            case TicketPricing.Free free -> entity.setPricingType("FREE");
            case TicketPricing.Paid paid -> {
                entity.setPricingType("PAID");
                entity.setPriceAmount(paid.amount());
                entity.setPriceCurrency(paid.currency());
            }
        }
        return entity;
    }

    public SocialEvent toDomain(SocialEventEntity entity) {
        EventDateRange dateRange = new EventDateRange(entity.getStartAt(), entity.getEndAt());

        RegistrationWindow regWindow = switch (entity.getRegWindowType()) {
            case "OPENS_AT" -> new RegistrationWindow.OpensAt(entity.getRegWindowStart());
            case "BOUNDED" -> new RegistrationWindow.Bounded(entity.getRegWindowStart(), entity.getRegWindowEnd());
            default -> new RegistrationWindow.AlwaysOpen();
        };

        // Reconstruct aggregate root via reflection/constructor mapping
        SocialEvent domain = reconstructDomain(
            new SocialEventId(entity.getId()),
            entity.getOrganizerId(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getCategory(),
            entity.getImageUrl(),
            EventVisibility.valueOf(entity.getVisibility()),
            SocialEventStatus.valueOf(entity.getStatus()),
            dateRange,
            regWindow
        );

        // Reconstruct Venue if present
        if (entity.getVenueName() != null) {
            Venue venue;
            if ("ASSIGNED_SEATING".equals(entity.getVenueSeatingType())) {
                venue = Venue.createAssignedSeating(
                    entity.getVenueName(),
                    entity.getVenueAddress(),
                    entity.getVenueCapacity(),
                    entity.getVenueLayoutId()
                );
            } else {
                venue = Venue.createGeneralAdmission(entity.getVenueName(), entity.getVenueAddress(), entity.getVenueCapacity());
            }
            domain.attachVenue(venue, entity.getOrganizerId());
        }

        // Reconstruct Ticket Types
        for (TicketTypeEntity ttEntity : entity.getTicketTypes()) {
            TicketPricing pricing = "PAID".equals(ttEntity.getPricingType())
                ? new TicketPricing.Paid(ttEntity.getPriceAmount(), ttEntity.getPriceCurrency())
                : new TicketPricing.Free();

            SalesWindow salesWindow = new SalesWindow(ttEntity.getSalesStart(), ttEntity.getSalesEnd());

            domain.addTicketType(
                ttEntity.getName(),
                ttEntity.getDescription(),
                pricing,
                ttEntity.getTotalQuantity(),
                salesWindow,
                ttEntity.getMaxPerOrder(),
                entity.getOrganizerId()
            );

            if (ttEntity.isHidden()) {
                TicketTypeId ttId = getTicketTypeIdFromDomain(domain, ttEntity.getName());
                if (ttId != null) domain.hideTicketType(ttId, entity.getOrganizerId());
            }
        }

        // Reconstruct Staff
        for (StaffAssignmentEmbeddable staff : entity.getStaffAssignments()) {
            domain.assignStaff(staff.getUserId(), StaffRole.valueOf(staff.getRole()), entity.getOrganizerId());
        }

        return domain;
    }

    private SocialEvent reconstructDomain(
        SocialEventId id,
        Long organizerId,
        String title,
        String description,
        String category,
        String imageUrl,
        EventVisibility visibility,
        SocialEventStatus status,
        EventDateRange dateRange,
        RegistrationWindow regWindow
    ) {
        try {
            Constructor<SocialEvent> constructor = SocialEvent.class.getDeclaredConstructor(
                SocialEventId.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                EventVisibility.class,
                EventDateRange.class,
                RegistrationWindow.class,
                Long.class
            );
            constructor.setAccessible(true);
            SocialEvent event = constructor.newInstance(
                id,
                organizerId,
                title,
                description,
                category,
                imageUrl,
                visibility,
                dateRange,
                regWindow,
                organizerId
            );

            Field statusField = SocialEvent.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(event, status);

            return event;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct SocialEvent domain model", e);
        }
    }

    private TicketTypeId getTicketTypeIdFromDomain(SocialEvent domain, String name) {
        return domain
            .getTicketTypes()
            .stream()
            .filter(tt -> tt.getName().equals(name))
            .map(TicketType::getId)
            .findFirst()
            .orElse(null);
    }
}
