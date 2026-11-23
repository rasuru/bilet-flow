package com.biletflow.biletflow.organizerhistory.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrganizerHistoryService {

    private static final Comparator<HistoryEntry> NEWEST_FIRST = Comparator.comparing(HistoryEntry::timestamp)
        .reversed()
        .thenComparing(e -> e.id().toString());

    private final List<HistorySource> sources;
    private final EventAccessPort events;
    private final CurrentActor actor;

    public OrganizerHistoryService(List<HistorySource> sources, EventAccessPort events, CurrentActor actor) {
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources cannot be null"));
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.actor = Objects.requireNonNull(actor, "actor cannot be null");
    }

    public HistoryPage history(HistoryQuery query) {
        Objects.requireNonNull(query, "query cannot be null");
        Long userId = actor.requireUserId();
        boolean manager = events.canManage(query.eventId(), userId);
        if (!manager && !actor.isAdministrator()) throw new AuthorizationException("Only the event's organizers can view its history");

        List<HistoryEntry> matching = sources
            .stream()
            .flatMap(source -> source.find(query.eventId(), query.from(), query.to()).stream())
            .filter(entry -> !entry.timestamp().isBefore(query.from()) && entry.timestamp().isBefore(query.to()))
            .filter(query::matches)
            .sorted(NEWEST_FIRST)
            .toList();

        long offset = (long) query.page() * query.size();
        List<HistoryEntry> page = matching.stream().skip(offset).limit(query.size()).toList();
        return new HistoryPage(page, query.page(), query.size(), matching.size());
    }
}
