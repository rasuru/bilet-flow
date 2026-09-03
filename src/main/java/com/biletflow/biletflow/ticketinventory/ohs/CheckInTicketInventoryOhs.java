package com.biletflow.biletflow.ticketinventory.ohs;

import com.biletflow.biletflow.ticketinventory.application.ticket.command.CheckInTicketCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.ReverseCheckInCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketValidationResult;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import java.util.UUID;

/**
 * Narrow synchronous contract intended for Check-in & Verification.
 *
 * QR signature verification should happen before the raw opaque ticketCode
 * reaches this contract.
 */
public interface CheckInTicketInventoryOhs {
    TicketValidationResult validate(UUID ticketCode, UUID expectedEventId);

    TicketView checkIn(CheckInTicketCommand command);

    TicketView reverseCheckIn(ReverseCheckInCommand command);
}
