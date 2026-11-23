package com.biletflow.biletflow.paymentsmonetization.persistence;

import com.biletflow.biletflow.common.ohs.OperationalCountsOhs;
import com.biletflow.biletflow.common.persistence.OperationalCountReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOperationalCounts implements OperationalCountsOhs {

    private final OperationalCountReader reader;

    public PaymentOperationalCounts(OperationalCountReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Count> countByStatus(UUID eventId) {
        var counts = new ArrayList<Count>();
        counts.addAll(reader.count("payments", "select status, event_id from pm_payment_work", eventId));
        counts.addAll(
            reader.count(
                "refunds",
                """
                select r.status, p.event_id
                from pm_refund_history r join pm_payment_work p on p.id = r.payment_id
                """,
                eventId
            )
        );
        counts.addAll(reader.count("paid_sales_activations", "select status, event_id from pm_event_activation", eventId));
        return List.copyOf(counts);
    }
}
