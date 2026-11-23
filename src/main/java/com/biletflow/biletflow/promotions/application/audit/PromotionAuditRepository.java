package com.biletflow.biletflow.promotions.application.audit;

import java.time.Instant;
import java.util.UUID;

public interface PromotionAuditRepository {
    void append(PromotionAuditRecord record);

    record PromotionAuditRecord(UUID id, UUID eventId, Instant timestamp, Long actorUserId, PromotionAuditType type, String description) {}

    enum PromotionAuditType {
        CAMPAIGN_CREATED,
        CAMPAIGN_ENDED,
        PROMO_CODE_CREATED,
        PROMO_CODE_DISABLED,
        PROMO_CODE_ENABLED,
    }
}
