package com.biletflow.biletflow.ticketinventory.persistence;

import com.biletflow.biletflow.common.ohs.OperationalCountsOhs;
import com.biletflow.biletflow.common.persistence.OperationalCountReader;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class TicketOperationalCounts implements OperationalCountsOhs {

    private final OperationalCountReader reader;

    public TicketOperationalCounts(OperationalCountReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Count> countByStatus(UUID eventId) {
        return reader.count("tickets", "select status, event_id from ticket_inventory_ticket", eventId);
    }
}
