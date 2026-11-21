package com.biletflow.biletflow.ordercheckout.persistence;

import com.biletflow.biletflow.common.ohs.OperationalCountsOhs;
import com.biletflow.biletflow.common.persistence.OperationalCountReader;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class OrderOperationalCounts implements OperationalCountsOhs {

    private final OperationalCountReader reader;

    public OrderOperationalCounts(OperationalCountReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Count> countByStatus(UUID eventId) {
        return reader.count("orders", "select status, event_id from oc_order", eventId);
    }
}
