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
public class SupportHistorySource implements HistorySource {

    private static final String SQL = """
    SELECT id, occurred_at, actor_user_id, action, support_case_id, details
    FROM support_case_audit
    WHERE event_id = :eventId AND occurred_at >= :from AND occurred_at < :to
      AND action IN ('CASE_OPENED', 'CASE_ASSIGNED', 'CASE_ESCALATED')
    """;

    private final EntityManager entityManager;

    public SupportHistorySource(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    public List<HistoryEntry> find(UUID eventId, Instant from, Instant to) {
        return query(entityManager, SQL, Map.of("eventId", eventId, "from", from, "to", to))
            .stream()
            .map(row -> {
                String action = text(row[3]);
                return new HistoryEntry(
                    uuid(row[0]),
                    instant(row[1]),
                    number(row[2]),
                    HistoryCategory.SUPPORT,
                    switch (action) {
                        case "CASE_OPENED" -> "SUPPORT_CASE_OPENED";
                        case "CASE_ESCALATED" -> "SUPPORT_CASE_ESCALATED";
                        default -> "SUPPORT_CASE_ASSIGNED";
                    },
                    "SUPPORT_CASE",
                    uuid(row[4]),
                    switch (action) {
                        case "CASE_OPENED" -> text(row[5]);
                        case "CASE_ESCALATED" -> "Support case escalated to platform administrators";
                        default -> "Support case assigned";
                    }
                );
            })
            .toList();
    }
}
