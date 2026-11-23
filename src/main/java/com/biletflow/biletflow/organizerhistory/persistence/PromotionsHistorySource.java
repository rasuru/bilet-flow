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
public class PromotionsHistorySource implements HistorySource {

    private static final String SQL = """
    SELECT id, timestamp, actor_user_id, type, description
    FROM promotions_activity_log
    WHERE event_id = :eventId AND timestamp >= :from AND timestamp < :to
    """;

    private final EntityManager entityManager;

    public PromotionsHistorySource(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public List<HistoryEntry> find(UUID eventId, Instant from, Instant to) {
        return query(entityManager, SQL, Map.of("eventId", eventId, "from", from, "to", to))
            .stream()
            .map(row -> {
                String type = text(row[3]);
                return new HistoryEntry(
                    uuid(row[0]),
                    instant(row[1]),
                    number(row[2]),
                    HistoryCategory.PROMOTIONS,
                    type,
                    type.startsWith("CAMPAIGN") ? "CAMPAIGN" : "PROMO_CODE",
                    null,
                    text(row[4])
                );
            })
            .toList();
    }
}
