package com.biletflow.biletflow.common.events;

import com.biletflow.biletflow.common.persistence.JsonCodec;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactional outbox; handler effects and acknowledgement commit together. Never drops failed events. */
@Component
public class JdbcEventBus implements EventBus {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcEventBus.class);
    private final JdbcTemplate jdbc;
    private final JsonCodec json;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Map<String, IntegrationEventHandler> handlers;

    public JdbcEventBus(
        JdbcTemplate jdbc,
        JsonCodec json,
        Clock clock,
        PlatformTransactionManager manager,
        List<IntegrationEventHandler> handlers
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
        this.json = Objects.requireNonNull(json, "json cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.tx = new TransactionTemplate(manager);
        this.handlers = handlers.stream().collect(Collectors.toMap(IntegrationEventHandler::eventType, Function.identity()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(IntegrationEvent event) {
        jdbc.update(
            "insert into common_outbox(id,payload,available_at) values (?,?,?) on conflict(id) do nothing",
            event.id(),
            json.write(event),
            Timestamp.from(clock.instant())
        );
    }

    @Scheduled(fixedDelayString = "${biletflow.payments.outbox-delay-ms:1000}")
    public void dispatch() {
        for (int i = 0; i < 50; i++) {
            UUID[] selected = new UUID[1];
            try {
                Boolean found = tx.execute(status -> {
                    var ids = jdbc.query(
                        "select id from common_outbox where delivered_at is null and available_at<=? order by available_at,id limit 1 for update skip locked",
                        (rs, n) -> rs.getObject(1, UUID.class),
                        Timestamp.from(clock.instant())
                    );
                    if (ids.isEmpty()) return false;
                    UUID id = ids.getFirst();
                    selected[0] = id;
                    var event = json.read(
                        jdbc.queryForObject("select payload from common_outbox where id=?", String.class, id),
                        IntegrationEvent.class
                    );
                    var handler = handlers.get(event.type());
                    if (handler == null) throw new IllegalStateException("No event consumer");
                    handler.handle(event);
                    jdbc.update("update common_outbox set delivered_at=? where id=?", Timestamp.from(clock.instant()), id);
                    return true;
                });
                if (!Boolean.TRUE.equals(found)) break;
            } catch (Exception failure) {
                if (selected[0] != null) tx.executeWithoutResult(s ->
                    jdbc.update(
                        "update common_outbox set attempts=attempts+1,available_at=? where id=? and delivered_at is null",
                        Timestamp.from(clock.instant().plusSeconds(30)),
                        selected[0]
                    )
                );
                LOG.warn("Integration event {} will be retried ({})", selected[0], failure.getClass().getSimpleName());
            }
        }
    }
}
