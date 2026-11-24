package com.biletflow.biletflow.organizerhistory.persistence;

import com.biletflow.biletflow.organizerhistory.application.HistoryCategory;
import com.biletflow.biletflow.organizerhistory.application.HistoryQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class HistoryActions {

    enum Entity {
        ROW,
        EVENT,
        NONE,
    }

    enum Description {
        DETAILS,
        LABEL,
        LABEL_WITH_DETAILS,
    }

    record Mapping(
        String raw,
        String action,
        HistoryCategory category,
        String entityType,
        Entity entity,
        Description description,
        String label
    ) {
        Mapping {
            Objects.requireNonNull(raw, "raw cannot be null");
            Objects.requireNonNull(action, "action cannot be null");
            Objects.requireNonNull(category, "category cannot be null");
            Objects.requireNonNull(entityType, "entityType cannot be null");
            Objects.requireNonNull(entity, "entity cannot be null");
            Objects.requireNonNull(description, "description cannot be null");
            if (description != Description.DETAILS) Objects.requireNonNull(label, "label is required for " + raw);
        }

        static Mapping details(String raw, HistoryCategory category, String entityType, Entity entity) {
            return new Mapping(raw, raw, category, entityType, entity, Description.DETAILS, null);
        }

        String describe(String details) {
            return switch (description) {
                case DETAILS -> details;
                case LABEL -> label;
                case LABEL_WITH_DETAILS -> details == null || details.isBlank() ? label : label + ": " + details;
            };
        }
    }

    private final Map<String, Mapping> byRaw;
    private final Set<String> ignored;

    HistoryActions(List<Mapping> mappings, Set<String> ignored) {
        Map<String, Mapping> map = new LinkedHashMap<>();
        for (Mapping mapping : mappings) {
            if (map.put(mapping.raw(), mapping) != null) throw new IllegalArgumentException("Duplicate mapping for " + mapping.raw());
        }
        this.byRaw = Map.copyOf(map);
        this.ignored = Set.copyOf(ignored);
        if (this.ignored.stream().anyMatch(byRaw::containsKey)) throw new IllegalArgumentException(
            "A raw type cannot be shown and ignored"
        );
    }

    List<String> rawTypesFor(HistoryQuery query) {
        return byRaw
            .values()
            .stream()
            .filter(mapping -> query.accepts(mapping.category(), mapping.action()))
            .map(Mapping::raw)
            .sorted()
            .toList();
    }

    Mapping require(String raw) {
        Mapping mapping = byRaw.get(raw);
        if (mapping == null) throw new IllegalStateException("No history mapping for " + raw);
        return mapping;
    }

    Set<String> shown() {
        return byRaw.keySet();
    }

    Set<String> ignored() {
        return ignored;
    }
}
