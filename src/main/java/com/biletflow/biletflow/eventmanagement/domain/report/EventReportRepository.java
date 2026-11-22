package com.biletflow.biletflow.eventmanagement.domain.report;

import java.util.Optional;
import java.util.UUID;

public interface EventReportRepository {
    Optional<EventReport> find(UUID id);
    Optional<EventReport> findByReporter(UUID eventId, long reporterId);
    void save(EventReport report);
}
