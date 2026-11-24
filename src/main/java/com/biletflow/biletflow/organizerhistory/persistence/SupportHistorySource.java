package com.biletflow.biletflow.organizerhistory.persistence;

import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Description;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Entity;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Mapping;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SupportHistorySource extends AuditLogHistorySource {

    static final HistoryActions ACTIONS = new HistoryActions(
        List.of(
            new Mapping(
                "CASE_OPENED",
                "SUPPORT_CASE_OPENED",
                HistoryCategory.SUPPORT,
                "SUPPORT_CASE",
                Entity.ROW,
                Description.DETAILS,
                null
            ),
            new Mapping(
                "CASE_ASSIGNED",
                "SUPPORT_CASE_ASSIGNED",
                HistoryCategory.SUPPORT,
                "SUPPORT_CASE",
                Entity.ROW,
                Description.LABEL,
                "Support case assigned"
            ),
            new Mapping(
                "CASE_STATUS_CHANGED",
                "SUPPORT_CASE_STATUS_CHANGED",
                HistoryCategory.SUPPORT,
                "SUPPORT_CASE",
                Entity.ROW,
                Description.LABEL_WITH_DETAILS,
                "Support case status changed"
            ),
            new Mapping(
                "CASE_ESCALATED",
                "SUPPORT_CASE_ESCALATED",
                HistoryCategory.SUPPORT,
                "SUPPORT_CASE",
                Entity.ROW,
                Description.LABEL,
                "Support case escalated to platform administrators"
            )
        ),
        Set.of("MESSAGE_ADDED")
    );

    private static final String SQL = """
    SELECT a.id AS id, a.occurred_at AS occurred, a.actor_user_id AS actor, a.action AS raw,
           a.support_case_id AS entity, a.details AS details
    FROM support_case_audit a
    WHERE a.event_id = :eventId AND a.occurred_at >= :from AND a.occurred_at < :to AND a.action IN (:types)
    """;

    public SupportHistorySource(EntityManager entityManager) {
        super("support", entityManager, ACTIONS);
    }

    @Override
    protected String select() {
        return SQL;
    }
}
