package com.biletflow.biletflow.paymentsmonetization.persistence;

import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.paymentsmonetization.application.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPaymentWorkRepository implements PaymentWorkRepository {

    private final JdbcTemplate jdbc;
    private final JsonCodec json;

    public JdbcPaymentWorkRepository(JdbcTemplate jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String nextInvoice() {
        // Halyk requires uniqueness of the last SIX digits too. Never wrap this sequence.
        Long n = jdbc.queryForObject("select nextval('pm_halyk_invoice_seq')", Long.class);
        return String.format(java.util.Locale.ROOT, "%06d", n);
    }

    @Override
    public Optional<PaymentWork> find(UUID id) {
        return query("select snapshot from pm_payment_work where id=?", id);
    }

    @Override
    public Optional<PaymentWork> findOrder(UUID id) {
        return query("select snapshot from pm_payment_work where order_id=?", id);
    }

    @Override
    public Optional<PaymentWork> findInvoice(String id) {
        return query("select snapshot from pm_payment_work where invoice_id=?", id);
    }

    private Optional<PaymentWork> query(String sql, Object id) {
        return jdbc
            .query(sql, (rs, n) -> json.read(rs.getString(1), PaymentSnapshot.class).domain(), id)
            .stream()
            .findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(PaymentWork w) {
        jdbc.update(
            "insert into pm_payment_work(id,order_id,event_id,organizer_id,payer_id,purpose,invoice_id,snapshot,next_poll) values (?,?,?,?,?,?,?,?,?)",
            w.id,
            w.orderId,
            w.eventId,
            w.organizerId,
            w.payerId,
            w.purpose,
            w.invoiceId,
            json.write(PaymentSnapshot.of(w)),
            Timestamp.from(Instant.EPOCH)
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(PaymentWork w) {
        jdbc.update("update pm_payment_work set snapshot=? where id=?", json.write(PaymentSnapshot.of(w)), w.id);
        if (w.isTicket()) w.payment
            .getRefund()
            .ifPresent(r ->
                jdbc.update(
                    "insert into pm_refund_history(id,payment_id,reason,requested_at,status,provider_reference,failure_reason,completed_at) values (?,?,?,?,?,?,?,?) " +
                        "on conflict(id) do update set status=excluded.status,provider_reference=excluded.provider_reference,failure_reason=excluded.failure_reason,completed_at=excluded.completed_at",
                    r.getId().value(),
                    w.id,
                    r.getReason(),
                    Timestamp.from(r.getRequestedAt()),
                    r.getStatus().name(),
                    r.getProviderReference(),
                    r.getFailureReason(),
                    r.getCompletedAt() == null ? null : Timestamp.from(r.getCompletedAt())
                )
            );
    }

    @Override
    public List<UUID> due(Instant now) {
        return jdbc.query(
            "select id from pm_payment_work where next_poll<=? and (lease_until is null or lease_until<=?) order by next_poll limit 50",
            (rs, n) -> rs.getObject(1, UUID.class),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    @Override
    public List<UUID> requiringReview() {
        return jdbc.query(
            "select id from pm_payment_work where (snapshot::jsonb->>'reviewRequired')='true' or (snapshot::jsonb->>'operationState')='UNKNOWN' order by next_poll limit 200",
            (rs, n) -> rs.getObject(1, UUID.class)
        );
    }

    @Override
    public boolean lease(UUID id, Instant now, Instant until) {
        return (
            jdbc.update(
                "update pm_payment_work set lease_until=? where id=? and (lease_until is null or lease_until<=?)",
                Timestamp.from(until),
                id,
                Timestamp.from(now)
            ) == 1
        );
    }

    @Override
    public void wake(UUID id, Instant when) {
        jdbc.update("update pm_payment_work set next_poll=least(next_poll,?) where id=?", Timestamp.from(when), id);
    }

    @Override
    public void schedule(UUID id, Instant when) {
        jdbc.update("update pm_payment_work set next_poll=?,lease_until=null where id=?", Timestamp.from(when), id);
    }
}
