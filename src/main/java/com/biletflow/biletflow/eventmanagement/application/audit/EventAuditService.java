package com.biletflow.biletflow.eventmanagement.application.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventAuditService {

    private final EventAuditRepository repository;
    private final Clock clock;

    public EventAuditService(EventAuditRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void record(UUID eventId, Long actorUserId, EventAuditRepository.EventAuditType type, String description) {
        recordAt(eventId, actorUserId, type, description, Instant.now(clock));
    }

    @Transactional
    public void recordAt(UUID eventId, Long actorUserId, EventAuditRepository.EventAuditType type,
                         String description, Instant timestamp) {
        EventAuditRepository.EventAuditRecord record = new EventAuditRepository.EventAuditRecord(
            UUID.randomUUID(), eventId, Objects.requireNonNull(timestamp), actorUserId, type, description
        );
        repository.append(record);
    }
}
