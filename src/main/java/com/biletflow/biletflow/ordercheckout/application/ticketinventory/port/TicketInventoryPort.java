package com.biletflow.biletflow.ordercheckout.application.ticketinventory.port;

import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.CheckoutCompleteSaleCommand;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.CheckoutHoldInventoryCommand;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.CheckoutReleaseHoldCommand;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.result.CheckoutHoldInventoryResult;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.result.CheckoutTicketTypeConfiguration;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.view.CheckoutTicketView;
import java.util.List;
import java.util.UUID;

public interface TicketInventoryPort {
    CheckoutHoldInventoryResult hold(CheckoutHoldInventoryCommand command);

    void releaseHold(CheckoutReleaseHoldCommand command);

    List<CheckoutTicketView> completeSale(CheckoutCompleteSaleCommand command);

    CheckoutTicketTypeConfiguration getTicketType(UUID ticketTypeId);
}
