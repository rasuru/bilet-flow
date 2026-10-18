package com.biletflow.biletflow.paymentsmonetization.persistence;

import com.biletflow.biletflow.paymentsmonetization.application.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Immutable demonstration ledger; unique operation keys prevent double posting. Fees are a local estimate. */
@Repository
public class JdbcPaymentLedger implements PaymentLedger {

    private final JdbcTemplate jdbc;

    public JdbcPaymentLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void charge(PaymentWork w) {
        post(w.id + ":charge", w, "CHARGE", w.amount, w.fee);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refund(PaymentWork w, UUID refundId) {
        post(w.id + ":refund", w, "REFUND", w.amount, BigDecimal.ZERO);
    }

    private void post(String key, PaymentWork w, String kind, BigDecimal amount, BigDecimal fee) {
        jdbc.update(
            "insert into pm_ledger(operation_key,payment_id,event_id,organizer_id,kind,amount,fee) values (?,?,?,?,?,?,?) on conflict(operation_key) do nothing",
            key,
            w.id,
            w.eventId,
            w.organizerId,
            kind,
            amount,
            fee
        );
    }

    @Override
    public Map<String, BigDecimal> summary(long organizer) {
        return jdbc.queryForObject(
            "select coalesce(sum(case when kind='CHARGE' then amount else 0 end),0) gross, " +
                "coalesce(sum(fee),0) fees, coalesce(sum(case when kind='REFUND' then amount else 0 end),0) refunds from pm_ledger where organizer_id=?",
            (rs, n) -> {
                BigDecimal gross = rs.getBigDecimal("gross"),
                    fees = rs.getBigDecimal("fees"),
                    refunds = rs.getBigDecimal("refunds");
                return Map.of(
                    "grossKzt",
                    gross,
                    "estimatedFeesKzt",
                    fees,
                    "refundsKzt",
                    refunds,
                    "estimatedNetKzt",
                    gross.subtract(fees).subtract(refunds)
                );
            },
            organizer
        );
    }
}
