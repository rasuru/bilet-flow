package com.biletflow.biletflow.organizerhistory.persistence;

import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Entity;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Mapping;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TicketInventoryHistorySource extends AuditLogHistorySource {

    static final HistoryActions ACTIONS = new HistoryActions(
        List.of(
            Mapping.details("TICKET_TYPE_CREATED", HistoryCategory.TICKETING, "TICKET_TYPE", Entity.ROW),
            Mapping.details("TICKET_PRICE_CHANGED", HistoryCategory.TICKETING, "TICKET_TYPE", Entity.ROW),
            Mapping.details("TICKET_CAPACITY_CHANGED", HistoryCategory.TICKETING, "TICKET_TYPE", Entity.ROW),
            Mapping.details("TICKET_CHECKED_IN", HistoryCategory.CHECK_IN, "TICKET", Entity.ROW),
            Mapping.details("CHECK_IN_REVERSED", HistoryCategory.CHECK_IN, "TICKET", Entity.ROW)
        ),
        Set.of()
    );

    private static final String SQL = """
    SELECT l.id AS id, l.timestamp AS occurred, l.actor_user_id AS actor, l.type AS raw,
           l.entity_id AS entity, l.description AS details
    FROM ticket_inventory_activity_log l
    WHERE l.event_id = :eventId AND l.timestamp >= :from AND l.timestamp < :to AND l.type IN (:types)
    """;

    public TicketInventoryHistorySource(EntityManager entityManager) {
        super("ticket-inventory", entityManager, ACTIONS);
    }

    @Override
    protected String select() {
        return SQL;
    }
}
