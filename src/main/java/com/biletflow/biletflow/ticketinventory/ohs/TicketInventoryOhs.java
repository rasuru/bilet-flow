package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldInventoryCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.ReleaseHoldCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.result.HoldInventoryResult;
import com.biletflow.biletflow.ticketinventory.application.sale.command.CompleteSaleCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TicketInventoryOhs {
    TicketSalesAvailability salesAvailability(UUID eventId, Instant now, boolean paidSalesPermitted);

    HoldInventoryResult hold(HoldInventoryCommand command);

    void releaseHold(ReleaseHoldCommand command);

    List<TicketView> completeSale(CompleteSaleCommand command);
}
