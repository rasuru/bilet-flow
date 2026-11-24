package com.biletflow.biletflow.organizerhistory.application;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record HistoryQuery(
    UUID eventId,
    Instant from,
    Instant to,
    Set<HistoryCategory> categories,
    Set<String> actions,
    int page,
    int size
) {
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
    public static final int MAX_DEPTH = 10_000;
    public static final Instant EARLIEST = Instant.EPOCH;
    public static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

    public HistoryQuery {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        from = from == null ? EARLIEST : from;
        to = to == null ? LATEST : to;
        if (!from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        actions =
            actions == null
                ? Set.of()
                : actions
                      .stream()
                      .filter(Objects::nonNull)
                      .map(String::strip)
                      .filter(a -> !a.isEmpty())
                      .map(a -> a.toUpperCase(Locale.ROOT))
                      .collect(Collectors.toUnmodifiableSet());
        if (page < 0) throw new IllegalArgumentException("page cannot be negative");
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        if ((long) page * size + size > MAX_DEPTH) throw new IllegalArgumentException(
            "Only the newest " + MAX_DEPTH + " entries can be paged; narrow the date range or filters"
        );
    }

    public static HistoryQuery all(UUID eventId) {
        return new HistoryQuery(eventId, null, null, null, null, 0, DEFAULT_SIZE);
    }

    public boolean accepts(HistoryCategory category, String action) {
        return (categories.isEmpty() || categories.contains(category)) && (actions.isEmpty() || actions.contains(action));
    }

    public long offset() {
        return (long) page * size;
    }
}
