package com.biletflow.biletflow.common.persistence;

import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL transaction locks also serialize creation before an aggregate row exists. */
@Component
public class TransactionLocks implements com.biletflow.biletflow.common.application.TransactionLocks {

    private final JdbcTemplate jdbc;

    public TransactionLocks(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, key);
    }
}
