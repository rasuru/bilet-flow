package com.biletflow.biletflow.ticketinventory.application.delivery;

import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.TicketEventDetails;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;

/** Current presentation of a canonical ticket; rendering never issues a new ticket or code. */
public record TicketDocument(TicketView ticket, String ticketType, TicketEventDetails event) {
    public boolean hasAdmissionCode() {
        return !event.cancelled() && (ticket.status() == TicketStatus.VALID || ticket.status() == TicketStatus.CHECKED_IN);
    }

    public String displayManualCode() {
        String code = ticket.manualCode();
        if (!hasAdmissionCode() || code == null) return null;
        return code.substring(0, 4) + "-" + code.substring(4);
    }
}
