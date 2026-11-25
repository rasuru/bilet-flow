package com.biletflow.biletflow.paymentsmonetization.persistence.payment;

import com.biletflow.biletflow.paymentsmonetization.application.MockPaymentScenarios;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMockPaymentScenarios implements MockPaymentScenarios {

    private final JdbcTemplate jdbc;

    public JdbcMockPaymentScenarios(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String nextInvoice() {
        return "M" + jdbc.queryForObject("select nextval('pm_mock_invoice_seq')", Long.class);
    }

    @Override
    public Optional<Attempt> find(UUID paymentId) {
        return jdbc
            .query(
                "select scenario, settles_at, refunded from pm_mock_attempt where payment_id = ?",
                (rs, row) ->
                    new Attempt(
                        Scenario.valueOf(rs.getString("scenario")),
                        rs.getTimestamp("settles_at").toInstant(),
                        rs.getBoolean("refunded")
                    ),
                paymentId
            )
            .stream()
            .findFirst();
    }

    @Override
    public void insert(UUID paymentId, Scenario scenario, Instant settlesAt) {
        jdbc.update(
            "insert into pm_mock_attempt(payment_id, scenario, settles_at, refunded) values (?, ?, ?, false)",
            paymentId,
            scenario.name(),
            Timestamp.from(settlesAt)
        );
    }

    @Override
    public void markRefunded(UUID paymentId) {
        if (jdbc.update("update pm_mock_attempt set refunded = true where payment_id = ?", paymentId) != 1) {
            throw new IllegalStateException("Mock payment has no settlement to refund");
        }
    }
}
