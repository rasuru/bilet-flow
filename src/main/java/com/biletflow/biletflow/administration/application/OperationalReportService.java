package com.biletflow.biletflow.administration.application;

import com.biletflow.biletflow.common.ohs.OperationalCountsOhs;
import com.biletflow.biletflow.common.ohs.OperationalCountsOhs.Count;
import com.biletflow.biletflow.common.security.CurrentActor;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalReportService {

    private final List<OperationalCountsOhs> sources;
    private final CurrentActor actor;

    public OperationalReportService(List<OperationalCountsOhs> sources, CurrentActor actor) {
        this.sources = List.copyOf(sources);
        this.actor = actor;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Count> countByStatus(UUID eventId) {
        actor.requireAdministrator();
        // All sources read from the same database snapshot.
        return sources.stream()
            .flatMap(source -> source.countByStatus(eventId).stream())
            .sorted(Comparator.comparing(Count::resource).thenComparing(Count::status))
            .toList();
    }
}
