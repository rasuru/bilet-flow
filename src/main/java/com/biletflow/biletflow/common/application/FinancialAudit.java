package com.biletflow.biletflow.common.application;

public interface FinancialAudit {
    default void record(String context, java.util.UUID aggregateId, Long actor, String action) {
        recordDetails(context, aggregateId, actor, action, null);
    }

    void recordDetails(String context, java.util.UUID aggregateId, Long actor, String action, String details);
}
