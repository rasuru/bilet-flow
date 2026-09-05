package com.biletflow.biletflow.ticketinventory.application.tickettype;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventTicketingConfiguration;
import com.biletflow.biletflow.ticketinventory.application.tickettype.command.*;
import com.biletflow.biletflow.ticketinventory.application.tickettype.view.TicketTypeView;
import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.*;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga.InventoryCounters;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketTypeService {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventInventoryRepository inventoryRepository;
    private final EventManagementPort eventManagementPort;
    private final CurrentActor currentActor;

    public TicketTypeService(
        TicketTypeRepository ticketTypeRepository,
        EventInventoryRepository inventoryRepository,
        EventManagementPort eventManagementPort,
        CurrentActor currentActor
    ) {
        this.ticketTypeRepository = Objects.requireNonNull(ticketTypeRepository);
        this.inventoryRepository = Objects.requireNonNull(inventoryRepository);
        this.eventManagementPort = Objects.requireNonNull(eventManagementPort);
        this.currentActor = Objects.requireNonNull(currentActor);
    }

    @Transactional
    public UUID create(CreateGeneralAdmissionTicketTypeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        authorizeTicketingManagement(command.eventId());

        EventInventory inventory = getOrCreateInventory(command.eventId());

        if (!(inventory.getMode() instanceof GeneralAdmissionInventory)) {
            throw new IllegalStateException("Event uses assigned seating; use the assigned-seating ticket-type command");
        }

        if (command.maxPerOrder() > command.capacity()) {
            throw new IllegalArgumentException("maxPerOrder cannot exceed ticket-type capacity");
        }

        TicketType ticketType = TicketType.createGeneralAdmission(
            command.eventId(),
            command.name(),
            command.description(),
            command.price(),
            command.maxPerOrder(),
            new SalesWindow(command.salesStartAt(), command.salesEndAt())
        );

        TicketType saved = ticketTypeRepository.save(ticketType);

        inventory.configureTicketType(saved.getId(), command.capacity());
        inventoryRepository.save(inventory);

        return saved.getId().value();
    }

    @Transactional
    public UUID create(CreateAssignedSeatingTicketTypeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        authorizeTicketingManagement(command.eventId());

        EventInventory inventory = getOrCreateInventory(command.eventId());

        if (!(inventory.getMode() instanceof AssignedSeatingInventory assigned)) {
            throw new IllegalStateException("Event uses general admission; use the general-admission ticket-type command");
        }

        if (command.maxPerOrder() > assigned.getTotalSeats().size()) {
            throw new IllegalArgumentException("maxPerOrder cannot exceed total assigned seats");
        }

        if (!assigned.containsPriceCategory(command.priceCategory())) {
            throw new IllegalArgumentException("No seats exist for price category: " + command.priceCategory());
        }

        TicketType ticketType = TicketType.createAssignedSeating(
            command.eventId(),
            command.name(),
            command.description(),
            command.price(),
            command.maxPerOrder(),
            new SalesWindow(command.salesStartAt(), command.salesEndAt()),
            command.priceCategory()
        );

        return ticketTypeRepository.save(ticketType).getId().value();
    }

    @Transactional
    public void update(UpdateGeneralAdmissionTicketTypeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        TicketType ticketType = requireTicketType(command.ticketTypeId());

        authorizeTicketingManagement(ticketType.getEventId());

        if (ticketType.isAssignedSeatingType()) {
            throw new IllegalStateException("Assigned-seating ticket type cannot be updated with the general-admission command");
        }

        EventInventory inventory = requireInventory(ticketType.getEventId());

        if (!(inventory.getMode() instanceof GeneralAdmissionInventory)) {
            throw new IllegalStateException("Ticket type belongs to an event that does not use general admission");
        }

        if (command.maxPerOrder() > command.capacity()) {
            throw new IllegalArgumentException("maxPerOrder cannot exceed ticket-type capacity");
        }

        inventory.changeTicketTypeCapacity(ticketType.getId(), command.capacity());

        ticketType.updateDetails(
            command.name(),
            command.description(),
            command.price(),
            new SalesWindow(command.salesStartAt(), command.salesEndAt()),
            command.maxPerOrder()
        );

        inventoryRepository.save(inventory);
        ticketTypeRepository.save(ticketType);
    }

    @Transactional
    public void update(UpdateAssignedSeatingTicketTypeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        TicketType ticketType = requireTicketType(command.ticketTypeId());

        authorizeTicketingManagement(ticketType.getEventId());

        if (!ticketType.isAssignedSeatingType()) {
            throw new IllegalStateException("General-admission ticket type cannot be updated with the assigned-seating command");
        }

        EventInventory inventory = requireInventory(ticketType.getEventId());

        if (!(inventory.getMode() instanceof AssignedSeatingInventory assigned)) {
            throw new IllegalStateException("Ticket type belongs to an event that does not use assigned seating");
        }

        long categorySeatCount = assigned
            .getSeatPriceCategories()
            .values()
            .stream()
            .filter(category -> Objects.equals(category, ticketType.getPriceCategory()))
            .count();

        if (command.maxPerOrder() > categorySeatCount) {
            throw new IllegalArgumentException("maxPerOrder cannot exceed seats available in the ticket type price category");
        }

        ticketType.updateDetails(
            command.name(),
            command.description(),
            command.price(),
            new SalesWindow(command.salesStartAt(), command.salesEndAt()),
            command.maxPerOrder()
        );

        ticketTypeRepository.save(ticketType);
    }

    @Transactional
    public void hide(HideTicketTypeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        TicketType ticketType = requireTicketType(command.ticketTypeId());

        authorizeTicketingManagement(ticketType.getEventId());

        ticketType.hide();
        ticketTypeRepository.save(ticketType);
    }

    @Transactional
    public void unhide(UnhideTicketTypeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        TicketType ticketType = requireTicketType(command.ticketTypeId());

        authorizeTicketingManagement(ticketType.getEventId());

        ticketType.unhide();
        ticketTypeRepository.save(ticketType);
    }

    public TicketTypeView get(UUID ticketTypeId) {
        TicketType ticketType = requireTicketType(ticketTypeId);

        EventInventory inventory = requireInventory(ticketType.getEventId());

        return toView(ticketType, inventory);
    }

    public List<TicketTypeView> listForEvent(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");

        List<TicketType> ticketTypes = ticketTypeRepository.findAllByEventId(eventId);

        if (ticketTypes.isEmpty()) {
            return List.of();
        }

        EventInventory inventory = requireInventory(eventId);

        return ticketTypes
            .stream()
            .map(ticketType -> toView(ticketType, inventory))
            .toList();
    }

    public List<TicketTypeView> listPublicForEvent(UUID eventId) {
        return listForEvent(eventId)
            .stream()
            .filter(ticketType -> ticketType.status() != TicketTypeStatus.HIDDEN)
            .toList();
    }

    public List<TicketTypeView> listForManagement(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        authorizeTicketingManagement(eventId);
        return listForEvent(eventId);
    }

    private void authorizeTicketingManagement(UUID eventId) {
        Long actorUserId = currentActor.requireUserId();

        if (!eventManagementPort.canManageTicketing(eventId, actorUserId)) {
            throw new AuthorizationException("User is not authorized to manage ticketing for event: " + eventId);
        }
    }

    private EventInventory getOrCreateInventory(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");

        Optional<EventInventory> existing = inventoryRepository.findByEventId(eventId);

        if (existing.isPresent()) {
            return existing.get();
        }

        EventTicketingConfiguration configuration = Objects.requireNonNull(
            eventManagementPort.getTicketingConfiguration(eventId),
            "Event Management returned null ticketing configuration"
        );

        if (!configuration.eventId().equals(eventId)) {
            throw new IllegalStateException("Event Management returned configuration for a different event");
        }

        InventoryMode mode = switch (configuration.inventoryKind()) {
            case GENERAL_ADMISSION -> GeneralAdmissionInventory.empty();
            case ASSIGNED_SEATING -> AssignedSeatingInventory.create(
                configuration
                    .seatPriceCategories()
                    .entrySet()
                    .stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(entry -> new SeatId(entry.getKey()), Map.Entry::getValue))
            );
        };

        EventInventory created = EventInventory.create(eventId, HoldExpiry.defaultExpiry(), mode);

        return inventoryRepository.save(created);
    }

    private TicketType requireTicketType(UUID ticketTypeId) {
        Objects.requireNonNull(ticketTypeId, "ticketTypeId cannot be null");

        return ticketTypeRepository
            .findById(new TicketTypeId(ticketTypeId))
            .orElseThrow(() -> new EntityNotFoundException("TicketType not found: " + ticketTypeId));
    }

    private EventInventory requireInventory(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");

        return inventoryRepository
            .findByEventId(eventId)
            .orElseThrow(() -> new EntityNotFoundException("EventInventory not found for event: " + eventId));
    }

    private TicketTypeView toView(TicketType ticketType, EventInventory inventory) {
        if (inventory.getMode() instanceof GeneralAdmissionInventory ga) {
            InventoryCounters counters = ga.getCounters().get(ticketType.getId());

            if (counters == null) {
                throw new IllegalStateException("No inventory configured for TicketType: " + ticketType.getId());
            }

            return new TicketTypeView(
                ticketType.getId().value(),
                ticketType.getEventId(),
                ticketType.getName(),
                ticketType.getDescription(),
                ticketType.getPrice(),
                ticketType.getMaxPerOrder(),
                ticketType.getSalesWindow().startAt(),
                ticketType.getSalesWindow().endAt(),
                ticketType.getStatus(),
                ticketType.getPriceCategory(),
                counters.totalCapacity(),
                counters.reserved(),
                counters.sold(),
                counters.available()
            );
        }

        return new TicketTypeView(
            ticketType.getId().value(),
            ticketType.getEventId(),
            ticketType.getName(),
            ticketType.getDescription(),
            ticketType.getPrice(),
            ticketType.getMaxPerOrder(),
            ticketType.getSalesWindow().startAt(),
            ticketType.getSalesWindow().endAt(),
            ticketType.getStatus(),
            ticketType.getPriceCategory(),
            null,
            null,
            null,
            null
        );
    }
}
