package com.biletflow.biletflow.organizerhistory.persistence;

import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Entity;
import com.biletflow.biletflow.organizerhistory.persistence.HistoryActions.Mapping;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PromotionsHistorySource extends AuditLogHistorySource {

    static final HistoryActions ACTIONS = new HistoryActions(
        List.of(
            Mapping.details("CAMPAIGN_CREATED", HistoryCategory.PROMOTIONS, "CAMPAIGN", Entity.NONE),
            Mapping.details("CAMPAIGN_ENDED", HistoryCategory.PROMOTIONS, "CAMPAIGN", Entity.NONE),
            Mapping.details("PROMO_CODE_CREATED", HistoryCategory.PROMOTIONS, "PROMO_CODE", Entity.NONE),
            Mapping.details("PROMO_CODE_DISABLED", HistoryCategory.PROMOTIONS, "PROMO_CODE", Entity.NONE),
            Mapping.details("PROMO_CODE_ENABLED", HistoryCategory.PROMOTIONS, "PROMO_CODE", Entity.NONE)
        ),
        Set.of()
    );

    private static final String SQL = """
    SELECT l.id AS id, l.timestamp AS occurred, l.actor_user_id AS actor, l.type AS raw,
           CAST(NULL AS uuid) AS entity, l.description AS details
    FROM promotions_activity_log l
    WHERE l.event_id = :eventId AND l.timestamp >= :from AND l.timestamp < :to AND l.type IN (:types)
    """;

    public PromotionsHistorySource(EntityManager entityManager) {
        super("promotions", entityManager, ACTIONS);
    }

    @Override
    protected String select() {
        return SQL;
    }
}
