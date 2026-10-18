package com.biletflow.biletflow.paymentsmonetization.persistence;

import com.biletflow.biletflow.paymentsmonetization.application.ActivationRepository;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.*;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePaymentId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcActivationRepository implements ActivationRepository {

    private final JdbcTemplate jdbc;

    public JdbcActivationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Activation> find(UUID event) {
        return jdbc
            .query(
                "select * from pm_event_activation where event_id=?",
                (rs, n) ->
                    new Activation(
                        new EventPaidSalesActivation(
                            event,
                            rs.getLong("organizer_id"),
                            rs.getTimestamp("created_at").toInstant(),
                            EventPaidSalesActivationStatus.valueOf(rs.getString("status")),
                            rs.getObject("fee_payment_id") == null
                                ? null
                                : new ActivationFeePaymentId(rs.getObject("fee_payment_id", UUID.class)),
                            rs.getTimestamp("suspended_at") == null ? null : rs.getTimestamp("suspended_at").toInstant(),
                            rs.getString("suspension_reason")
                        ),
                        rs.getObject("payment_id", UUID.class),
                        rs.getBoolean("verification_simulated"),
                        rs.getString("terms_version")
                    ),
                event
            )
            .stream()
            .findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Activation a) {
        var d = a.aggregate();
        jdbc.update(
            "insert into pm_event_activation(event_id,organizer_id,status,payment_id,fee_payment_id,created_at,verification_simulated,terms_version,suspended_at,suspension_reason) " +
                "values (?,?,?,?,?,?,?,?,?,?) on conflict(event_id) do update set status=excluded.status,payment_id=excluded.payment_id,fee_payment_id=excluded.fee_payment_id,suspended_at=excluded.suspended_at,suspension_reason=excluded.suspension_reason",
            d.getEventId(),
            d.getOrganizerId(),
            d.getStatus().name(),
            a.paymentId(),
            d.getFeePaymentId() == null ? null : d.getFeePaymentId().value(),
            Timestamp.from(d.getCreatedAt()),
            a.verificationSimulated(),
            a.termsVersion(),
            d.getSuspendedAt() == null ? null : Timestamp.from(d.getSuspendedAt()),
            d.getSuspensionReason()
        );
    }
}
