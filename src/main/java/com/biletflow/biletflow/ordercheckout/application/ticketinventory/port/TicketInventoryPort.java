package com.biletflow.biletflow.ordercheckout.application.ticketinventory.port;

import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.*;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.result.*;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.view.*;
import java.util.List;

public interface TicketInventoryPort {
   Checkout HoldInventoryResult hold(CheckoutHoldInventoryCommand command);

    void releaseHold(ReleaseHoldCommand command);

    List<CheckoutTicketView> completeSale(CheckoutCompleteSaleCommand command);

    CheckoutTicketTypeConfiguration getTicketType(UUID ticketTypeId);
}
