package com.biletflow.biletflow.ticketinventory.application.ohs;

import com.biletflow.biletflow.ticketinventory.application.ticket.TicketApplicationService;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.CheckInTicketCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.ReverseCheckInCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketValidationResult;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultCheckInTicketInventoryOhs implements CheckInTicketInventoryOhs {

    private final TicketApplicationService ticketService;

    public DefaultCheckInTicketInventoryOhs(TicketApplicationService ticketService) {
        this.ticketService = Objects.requireNonNull(ticketService);
    }

    @Override
    public TicketValidationResult validate(UUID ticketCode, UUID expectedEventId) {
        return ticketService.validate(ticketCode, expectedEventId);
    }

    @Override
    public TicketView checkIn(CheckInTicketCommand command) {
        return ticketService.checkIn(command);
    }

    @Override
    public TicketView reverseCheckIn(ReverseCheckInCommand command) {
        return ticketService.reverseCheckIn(command);
    }
}
