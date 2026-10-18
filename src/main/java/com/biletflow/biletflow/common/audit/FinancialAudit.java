package com.biletflow.biletflow.common.audit;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Append-only business audit. Details are fixed application codes, not provider responses. */
@Component
public class FinancialAudit implements com.biletflow.biletflow.common.application.FinancialAudit {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public FinancialAudit(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String context, UUID aggregateId, Long actor, String action) {
        recordDetails(context, aggregateId, actor, action, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDetails(String context, UUID aggregateId, Long actor, String action, String details) {
        jdbc.update(
            "insert into common_financial_audit(id, context, aggregate_id, actor_id, action, occurred_at, details) values (?,?,?,?,?,?,?)",
            UUID.randomUUID(),
            context,
            aggregateId,
            actor,
            action,
            Timestamp.from(clock.instant()),
            details
        );
    }
}
