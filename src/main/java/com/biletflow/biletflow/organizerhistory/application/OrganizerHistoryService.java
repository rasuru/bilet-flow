package com.biletflow.biletflow.organizerhistory.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrganizerHistoryService {

    private final List<HistorySource> sources;
    private final EventAccessPort events;
    private final CurrentActor actor;

    public OrganizerHistoryService(List<HistorySource> sources, EventAccessPort events, CurrentActor actor) {
        this.sources = Objects.requireNonNull(sources, "sources cannot be null")
            .stream()
            .sorted((left, right) -> left.name().compareTo(right.name()))
            .toList();
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.actor = Objects.requireNonNull(actor, "actor cannot be null");
    }

    public HistoryPage history(HistoryQuery query) {
        Objects.requireNonNull(query, "query cannot be null");
        Long userId = actor.requireUserId();
        boolean organizer = events.canManage(query.eventId(), userId);
        if (!organizer && !actor.isAdministrator()) throw new AuthorizationException("Only the event's organizer can view its history");

        long total = sources
            .stream()
            .mapToLong(source -> source.count(query))
            .sum();
        int limit = Math.toIntExact(query.offset() + query.size());
        List<List<HistoryEntry>> newest = sources
            .stream()
            .map(source -> source.newest(query, limit))
            .toList();
        List<HistoryEntry> merged = merge(newest, limit);
        List<HistoryEntry> page = merged.stream().skip(query.offset()).limit(query.size()).toList();
        return new HistoryPage(page, query.page(), query.size(), total);
    }

    private static List<HistoryEntry> merge(List<List<HistoryEntry>> perSource, int limit) {
        int[] positions = new int[perSource.size()];
        List<HistoryEntry> merged = new ArrayList<>(limit);
        while (merged.size() < limit) {
            int next = -1;
            for (int source = 0; source < perSource.size(); source++) {
                if (positions[source] >= perSource.get(source).size()) continue;
                HistoryEntry candidate = perSource.get(source).get(positions[source]);
                if (next < 0 || candidate.timestamp().isAfter(perSource.get(next).get(positions[next]).timestamp())) next = source;
            }
            if (next < 0) break;
            merged.add(perSource.get(next).get(positions[next]++));
        }
        return merged;
    }
}
