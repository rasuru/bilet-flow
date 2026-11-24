package com.biletflow.biletflow.organizerhistory.persistence;

import static com.biletflow.biletflow.organizerhistory.persistence.NativeRows.*;

import com.biletflow.biletflow.organizerhistory.application.HistoryEntry;
import com.biletflow.biletflow.organizerhistory.application.HistoryQuery;
import com.biletflow.biletflow.organizerhistory.application.HistorySource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract class AuditLogHistorySource implements HistorySource {

    private final String name;
    private final EntityManager entityManager;
    private final HistoryActions actions;

    AuditLogHistorySource(String name, EntityManager entityManager, HistoryActions actions) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager cannot be null");
        this.actions = Objects.requireNonNull(actions, "actions cannot be null");
    }

    protected abstract String select();

    protected Map<String, Object> extraParameters() {
        return Map.of();
    }

    @Override
    public String name() {
        return name;
    }

    HistoryActions actions() {
        return actions;
    }

    @Override
    public long count(HistoryQuery query) {
        List<String> types = actions.rawTypesFor(query);
        if (types.isEmpty()) return 0;
        Object result = bind(
            entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + select() + ") history"),
            query,
            types
        ).getSingleResult();
        return ((Number) result).longValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<HistoryEntry> newest(HistoryQuery query, int limit) {
        List<String> types = actions.rawTypesFor(query);
        if (types.isEmpty() || limit < 1) return List.of();
        Query page = bind(entityManager.createNativeQuery(select() + " ORDER BY occurred DESC, id DESC"), query, types);
        page.setMaxResults(limit);
        List<Object[]> rows = page.getResultList();
        return rows
            .stream()
            .map(row -> toEntry(query, row))
            .toList();
    }

    private Query bind(Query nativeQuery, HistoryQuery query, List<String> types) {
        nativeQuery.setParameter("eventId", query.eventId());
        nativeQuery.setParameter("from", query.from());
        nativeQuery.setParameter("to", query.to());
        nativeQuery.setParameter("types", types);
        extraParameters().forEach(nativeQuery::setParameter);
        return nativeQuery;
    }

    private HistoryEntry toEntry(HistoryQuery query, Object[] row) {
        HistoryActions.Mapping mapping = actions.require(text(row[3]));
        return new HistoryEntry(
            uuid(row[0]),
            instant(row[1]),
            number(row[2]),
            mapping.category(),
            mapping.action(),
            mapping.entityType(),
            switch (mapping.entity()) {
                case ROW -> uuid(row[4]);
                case EVENT -> query.eventId();
                case NONE -> null;
            },
            mapping.describe(text(row[5]))
        );
    }
}
