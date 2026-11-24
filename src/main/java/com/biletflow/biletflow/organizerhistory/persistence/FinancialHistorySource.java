package com.biletflow.biletflow.organizerhistory.persistence;

import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Description;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Entity;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Mapping;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FinancialHistorySource extends AuditLogHistorySource {

    static final List<String> REFUND_ACTIONS = List.of(
        "ORDER_REFUND_REQUESTED",
        "UNFULFILLED_ORDER_REFUND_REQUESTED",
        "ORDER_REFUND_COMPLETED",
        "ORDER_REFUND_FAILED",
        "FREE_ORDER_CANCELLED"
    );

    static final List<String> PAID_SALES_ACTIONS = List.of("PAID_SALES_SUSPENDED", "PAID_SALES_REINSTATED");

    static final List<String> ACTIVATION_ACTIONS = List.of(
        "ACTIVATION_REQUESTED_SIMULATED_VERIFICATION",
        "ACTIVATION_FEE_PAID",
        "ACTIVATION_FEE_REFUNDED",
        "ACTIVATION_FEE_COMPENSATING_REFUND_REQUESTED"
    );

    static final HistoryActions ACTIONS = new HistoryActions(
        List.of(
            refund("ORDER_REFUND_REQUESTED", "Order refund requested"),
            refund("UNFULFILLED_ORDER_REFUND_REQUESTED", "Refund requested for an unfulfilled payment"),
            refund("ORDER_REFUND_COMPLETED", "Order refund completed"),
            refund("ORDER_REFUND_FAILED", "Order refund failed"),
            refund("FREE_ORDER_CANCELLED", "Free order cancelled by the organizer"),
            payment("PAID_SALES_SUSPENDED", "EVENT", Entity.EVENT, "Paid sales suspended"),
            payment("PAID_SALES_REINSTATED", "EVENT", Entity.EVENT, "Paid sales reinstated"),
            payment("ACTIVATION_REQUESTED_SIMULATED_VERIFICATION", "PAYMENT", Entity.ROW, "Paid sales activation requested"),
            payment("ACTIVATION_FEE_PAID", "PAYMENT", Entity.ROW, "Activation fee paid"),
            payment("ACTIVATION_FEE_REFUNDED", "PAYMENT", Entity.ROW, "Activation fee refunded"),
            payment("ACTIVATION_FEE_COMPENSATING_REFUND_REQUESTED", "PAYMENT", Entity.ROW, "Activation fee refund requested")
        ),
        Set.of()
    );

    private static final String SQL = """
    SELECT a.id AS id, a.occurred_at AS occurred, a.actor_id AS actor, a.action AS raw,
           a.aggregate_id AS entity, a.details AS details
    FROM common_financial_audit a
    WHERE a.occurred_at >= :from AND a.occurred_at < :to AND a.action IN (:types) AND (
        (a.context = 'ordercheckout' AND a.action IN (:refundActions)
            AND a.aggregate_id IN (SELECT c.id FROM oc_checkout c WHERE c.event_id = :eventId))
        OR (a.context = 'payments' AND a.action IN (:paidSalesActions) AND a.aggregate_id = :eventId)
        OR (a.context = 'payments' AND a.action IN (:activationActions)
            AND a.aggregate_id IN (SELECT w.id FROM pm_payment_work w WHERE w.event_id = :eventId AND w.purpose = 'ACTIVATION'))
    )
    """;

    public FinancialHistorySource(EntityManager entityManager) {
        super("financial", entityManager, ACTIONS);
    }

    @Override
    protected String select() {
        return SQL;
    }

    @Override
    protected Map<String, Object> extraParameters() {
        return Map.of("refundActions", REFUND_ACTIONS, "paidSalesActions", PAID_SALES_ACTIONS, "activationActions", ACTIVATION_ACTIONS);
    }

    private static Mapping refund(String raw, String label) {
        return new Mapping(raw, raw, HistoryCategory.REFUNDS, "ORDER", Entity.ROW, Description.LABEL_WITH_DETAILS, label);
    }

    private static Mapping payment(String raw, String entityType, Entity entity, String label) {
        return new Mapping(raw, raw, HistoryCategory.PAYMENTS, entityType, entity, Description.LABEL_WITH_DETAILS, label);
    }
}
