package com.biletflow.biletflow.ticketinventory.application.tickettype;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.TicketPurchaseAvailability;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.ticketinventory.application.tickettype.port.PaidSalesPort;
import com.biletflow.biletflow.ticketinventory.application.tickettype.view.PublicTicketTypeView;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeStatus;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicTicketTypeService {
    private final TicketTypeRepository types;
    private final EventInventoryRepository inventories;
    private final EventManagementPort events;
    private final PaidSalesPort payments;
    private final Clock clock;

    public PublicTicketTypeService(
        TicketTypeRepository types, EventInventoryRepository inventories, EventManagementPort events,
        PaidSalesPort payments, Clock clock
    ) {
        this.types = Objects.requireNonNull(types);
        this.inventories = Objects.requireNonNull(inventories);
        this.events = Objects.requireNonNull(events);
        this.payments = Objects.requireNonNull(payments);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<PublicTicketTypeView> list(UUID eventId) {
        var now = clock.instant();
        var scope = events.publicSalesScope(eventId, now);
        if (!scope.publiclyReadable()) throw new EntityNotFoundException("Event not found: " + eventId);
        var visibleTypes = types.findAllByEventId(eventId).stream()
            .filter(type -> type.getStatus() != TicketTypeStatus.HIDDEN).toList();
        if (visibleTypes.isEmpty()) return List.of();
        var inventory = inventories.findByEventId(eventId)
            .orElseThrow(() -> new EntityNotFoundException("EventInventory not found for event: " + eventId));
        inventory.cleanUpExpiredHolds(now);
        boolean paidSalesPermitted = scope.registrationOpen() && visibleTypes.stream().anyMatch(type -> !type.isFree())
            && payments.permitsPaidSales(eventId);
        return visibleTypes.stream().map(type -> PublicTicketTypeView.from(
            TicketTypeService.toView(type, inventory),
            TicketPurchaseAvailability.evaluate(type, inventory.getMode(), now, scope.registrationOpen(), paidSalesPermitted)
        )).toList();
    }
}

