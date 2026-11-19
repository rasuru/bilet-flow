package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.EventInventoryService;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldInventoryCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.ReleaseHoldCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.result.HoldInventoryResult;
import com.biletflow.biletflow.ticketinventory.application.sale.TicketSaleService;
import com.biletflow.biletflow.ticketinventory.application.sale.command.CompleteSaleCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultTicketInventoryOhs implements TicketInventoryOhs {

    private final EventInventoryService inventoryService;
    private final TicketSaleService saleService;

    public DefaultTicketInventoryOhs(EventInventoryService inventoryService, TicketSaleService saleService) {
        this.inventoryService = Objects.requireNonNull(inventoryService);
        this.saleService = Objects.requireNonNull(saleService);
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
}
