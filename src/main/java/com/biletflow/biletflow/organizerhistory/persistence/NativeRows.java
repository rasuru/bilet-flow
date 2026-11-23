package com.biletflow.biletflow.organizerhistory.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class NativeRows {

    private NativeRows() {}

    @SuppressWarnings("unchecked")
    static List<Object[]> query(EntityManager entityManager, String sql, Map<String, Object> parameters) {
        var query = entityManager.createNativeQuery(sql);
        parameters.forEach(query::setParameter);
        return query.getResultList();
    }

    static Instant instant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case OffsetDateTime offset -> offset.toInstant();
            case ZonedDateTime zoned -> zoned.toInstant();
            case java.sql.Timestamp timestamp -> timestamp.toInstant();
            default -> throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
        };
    }

    static UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID id) return id;
        return UUID.fromString(value.toString());
    }

    static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
