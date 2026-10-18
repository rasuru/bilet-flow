package com.biletflow.biletflow.paymentsmonetization.persistence;

import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentLedger;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaPaymentLedger implements PaymentLedger {

    private final EntityManager entities;
    private final TransactionLocks locks;
    private final Clock clock;

    public JpaPaymentLedger(EntityManager entities, TransactionLocks locks, Clock clock) {
        this.entities = entities;
        this.locks = locks;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void charge(PaymentWork work) {
        post(work, "CHARGE", work.fee);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refund(PaymentWork work, UUID refundId) {
        post(work, "REFUND", BigDecimal.ZERO);
    }

    private void post(PaymentWork work, String kind, BigDecimal fee) {
        locks.lock("ledger:" + work.id);
        String key = work.id + ":" + kind.toLowerCase(Locale.ROOT);
        if (entities.find(LedgerEntryJpaEntity.class, key) != null) return;
        var row = new LedgerEntryJpaEntity();
        row.id = key;
        row.paymentId = work.id;
        row.eventId = work.eventId;
        row.organizerId = work.organizerId;
        row.kind = kind;
        row.amount = work.amount;
        row.fee = fee;
        row.createdAt = clock.instant();
        entities.persist(row);
    }

    @Override
    public Map<String, BigDecimal> summary(long organizer) {
        Object[] totals = entities
            .createQuery(
                "select coalesce(sum(case when l.kind = 'CHARGE' then l.amount else 0 end),0), coalesce(sum(l.fee),0), coalesce(sum(case when l.kind = 'REFUND' then l.amount else 0 end),0) from LedgerEntryJpaEntity l where l.organizerId = :organizer",
                Object[].class
            )
            .setParameter("organizer", organizer)
            .getSingleResult();
        BigDecimal gross = decimal(totals[0]),
            fees = decimal(totals[1]),
            refunds = decimal(totals[2]);
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
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }
}
