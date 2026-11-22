package com.biletflow.biletflow.eventmanagement.persistence;

import com.biletflow.biletflow.common.ohs.OperationalCountsOhs;
import com.biletflow.biletflow.common.persistence.OperationalCountReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class EventOperationalCounts implements OperationalCountsOhs {

    private final OperationalCountReader reader;

    public EventOperationalCounts(OperationalCountReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Count> countByStatus(UUID eventId) {
        var counts = new ArrayList<Count>();
        counts.addAll(reader.count("events", "select status, id as event_id from event_management_event", eventId));
        counts.addAll(reader.count("event_reports", "select status, event_id from event_management_report", eventId));
        return List.copyOf(counts);
    }
}
