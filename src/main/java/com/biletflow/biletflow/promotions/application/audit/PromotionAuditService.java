package com.biletflow.biletflow.promotions.application.audit;

import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditRecord;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PromotionAuditService {

    private final PromotionAuditRepository repository;
    private final Clock clock;

    public PromotionAuditService(PromotionAuditRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public void record(UUID eventId, Long actorUserId, PromotionAuditType type, String description) {
        repository.append(new PromotionAuditRecord(UUID.randomUUID(), eventId, Instant.now(clock), actorUserId, type, description));
    }
}
