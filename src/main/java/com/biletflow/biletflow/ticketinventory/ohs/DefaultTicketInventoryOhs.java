package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.EventInventoryService;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldInventoryCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.ReleaseHoldCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.result.HoldInventoryResult;
import com.biletflow.biletflow.ticketinventory.application.sale.TicketSaleService;
import com.biletflow.biletflow.ticketinventory.application.sale.command.CompleteSaleCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketSupportReferenceView;
import com.biletflow.biletflow.ticketinventory.application.ticket.TicketService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultTicketInventoryOhs implements TicketInventoryOhs {

    private final EventInventoryService inventoryService;
    private final TicketSaleService saleService;
    private final TicketService ticketService;

    public DefaultTicketInventoryOhs(EventInventoryService inventoryService, TicketSaleService saleService,
                                     TicketService ticketService) {
        this.inventoryService = Objects.requireNonNull(inventoryService);
        this.saleService = Objects.requireNonNull(saleService);
        this.ticketService = Objects.requireNonNull(ticketService);
    }

    @Override
    public TicketSalesAvailability salesAvailability(UUID eventId, Instant now, boolean paidSalesPermitted) {
        return inventoryService.salesAvailability(eventId, now, paidSalesPermitted);
    }

    @Override
    public HoldInventoryResult hold(HoldInventoryCommand command) {
        return inventoryService.hold(command);
    }

    @Override
    public void releaseHold(ReleaseHoldCommand command) {
        inventoryService.releaseHold(command);
    }

    @Override
    public List<TicketView> completeSale(CompleteSaleCommand command) {
        return saleService.complete(command);
    }

    @Override
    public Optional<TicketSupportReferenceView> findSupportReference(UUID ticketId) {
        return ticketService.findSupportReference(ticketId);
    }
}
