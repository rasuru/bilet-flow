package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.EventInventoryService;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldInventoryCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.ReleaseHoldCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.result.HoldInventoryResult;
import com.biletflow.biletflow.ticketinventory.application.sale.TicketSaleService;
import com.biletflow.biletflow.ticketinventory.application.sale.command.CompleteSaleCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import java.util.UUID;
import com.biletflow.biletflow.ticketinventory.application.tickettype.view.TicketTypeView;
import com.biletflow.biletflow.ticketinventory.application.tickettype.TicketTypeService;
@Service
public class DefaultTicketInventoryOhs implements TicketInventoryOhs {

    private final EventInventoryService inventoryService;
    private final TicketSaleService saleService;
    private final TicketTypeService ticketTypeService;

    public DefaultTicketInventoryOhs(
        EventInventoryService inventoryService,
        TicketSaleService saleService,
        TicketTypeService ticketTypeService
    ) {
        this.inventoryService = Objects.requireNonNull(inventoryService);
        this.saleService = Objects.requireNonNull(saleService);
        this.ticketTypeService = Objects.requireNonNull(ticketTypeService);
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
    public TicketTypeView getTicketType(UUID ticketTypeId) {
        return ticketTypeService.get(ticketTypeId);
    }
}
