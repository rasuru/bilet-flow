package com.biletflow.biletflow.organizerhistory.persistence;

import static com.biletflow.biletflow.organizerhistory.persistence.NativeRows.*;

import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.application.HistoryEntry;
import com.biletflow.biletflow.organizerhistory.application.HistorySource;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FinancialHistorySource implements HistorySource {

    static final Map<String, String> REFUND_ACTIONS = Map.of(
        "ORDER_REFUND_REQUESTED",
        "Order refund requested",
        "UNFULFILLED_ORDER_REFUND_REQUESTED",
        "Refund requested for an unfulfilled payment",
        "ORDER_REFUND_COMPLETED",
        "Order refund completed",
        "ORDER_REFUND_FAILED",
        "Order refund failed",
        "FREE_ORDER_CANCELLED",
        "Free order cancelled by the organizer"
    );

    static final Map<String, String> PAID_SALES_ACTIONS = Map.of(
        "PAID_SALES_SUSPENDED",
        "Paid sales suspended",
        "PAID_SALES_REINSTATED",
        "Paid sales reinstated"
    );

    static final Map<String, String> ACTIVATION_ACTIONS = Map.of(
        "ACTIVATION_REQUESTED_SIMULATED_VERIFICATION",
        "Paid sales activation requested",
        "ACTIVATION_FEE_PAID",
        "Activation fee paid",
        "ACTIVATION_FEE_REFUNDED",
        "Activation fee refunded",
        "ACTIVATION_FEE_COMPENSATING_REFUND_REQUESTED",
        "Activation fee refund requested"
    );

    private static final String SQL = """
    SELECT a.id, a.occurred_at, a.actor_id, a.action, a.aggregate_id, a.details
    FROM common_financial_audit a
    WHERE a.occurred_at >= :from AND a.occurred_at < :to AND (
        (a.context = 'ordercheckout' AND a.action IN (:refundActions)
            AND a.aggregate_id IN (SELECT c.id FROM oc_checkout c WHERE c.event_id = :eventId))
        OR (a.context = 'payments' AND a.action IN (:paidSalesActions) AND a.aggregate_id = :eventId)
        OR (a.context = 'payments' AND a.action IN (:activationActions)
            AND a.aggregate_id IN (SELECT w.id FROM pm_payment_work w WHERE w.event_id = :eventId AND w.purpose = 'ACTIVATION'))
    )
    """;

    private final EntityManager entityManager;

    public FinancialHistorySource(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public List<HistoryEntry> find(UUID eventId, Instant from, Instant to) {
        var parameters = Map.<String, Object>of(
            "eventId",
            eventId,
            "from",
            from,
            "to",
            to,
            "refundActions",
            List.copyOf(REFUND_ACTIONS.keySet()),
            "paidSalesActions",
            List.copyOf(PAID_SALES_ACTIONS.keySet()),
            "activationActions",
            List.copyOf(ACTIVATION_ACTIONS.keySet())
        );
        return query(entityManager, SQL, parameters)
            .stream()
            .map(row -> toEntry(eventId, row))
            .toList();
    }

    private static HistoryEntry toEntry(UUID eventId, Object[] row) {
        String action = text(row[3]);
        String details = text(row[5]);
        if (REFUND_ACTIONS.containsKey(action)) {
            return entry(row, HistoryCategory.REFUNDS, action, "ORDER", uuid(row[4]), REFUND_ACTIONS.get(action), details);
        }
        if (PAID_SALES_ACTIONS.containsKey(action)) {
            return entry(row, HistoryCategory.PAYMENTS, action, "EVENT", eventId, PAID_SALES_ACTIONS.get(action), details);
        }
        return entry(row, HistoryCategory.PAYMENTS, action, "PAYMENT", uuid(row[4]), ACTIVATION_ACTIONS.get(action), details);
    }

    private static HistoryEntry entry(
        Object[] row,
        HistoryCategory category,
        String action,
        String entityType,
        UUID entityId,
        String label,
        String details
    ) {
        String description = details == null || details.isBlank() ? label : label + ": " + details;
        return new HistoryEntry(uuid(row[0]), instant(row[1]), number(row[2]), category, action, entityType, entityId, description);
    }
}
