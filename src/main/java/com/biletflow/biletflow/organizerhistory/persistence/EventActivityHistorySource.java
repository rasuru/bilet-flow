package com.biletflow.biletflow.organizerhistory.persistence;

import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Entity;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Mapping;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EventActivityHistorySource extends AuditLogHistorySource {

    static final HistoryActions ACTIONS = new HistoryActions(
        List.of(
            Mapping.details("CREATED", HistoryCategory.EVENT, "EVENT", Entity.EVENT),
            Mapping.details("UPDATED", HistoryCategory.EVENT, "EVENT", Entity.EVENT),
            Mapping.details("PUBLISHED", HistoryCategory.EVENT, "EVENT", Entity.EVENT),
            Mapping.details("UNPUBLISHED", HistoryCategory.EVENT, "EVENT", Entity.EVENT),
            Mapping.details("CANCELLED", HistoryCategory.EVENT, "EVENT", Entity.EVENT),
            Mapping.details("SUSPENDED", HistoryCategory.EVENT, "EVENT", Entity.EVENT),
            Mapping.details("REINSTATED", HistoryCategory.EVENT, "EVENT", Entity.EVENT),
            Mapping.details("VENUE_ATTACHED", HistoryCategory.EVENT, "EVENT", Entity.EVENT),
            Mapping.details("STAFF_ASSIGNED", HistoryCategory.EVENT, "EVENT", Entity.EVENT),
            Mapping.details("STAFF_REMOVED", HistoryCategory.EVENT, "EVENT", Entity.EVENT)
        ),
        Set.of("SUPPORT_CASE_STATUS_CHANGED")
    );

    private static final String SQL = """
    SELECT l.id AS id, l.timestamp AS occurred, l.actor_user_id AS actor, l.type AS raw,
           CAST(NULL AS uuid) AS entity, l.description AS details
    FROM event_activity_log l
    WHERE l.event_id = :eventId AND l.timestamp >= :from AND l.timestamp < :to AND l.type IN (:types)
    """;

    public EventActivityHistorySource(EntityManager entityManager) {
        super("event-management", entityManager, ACTIONS);
    }

    @Override
    protected String select() {
        return SQL;
    }
}
