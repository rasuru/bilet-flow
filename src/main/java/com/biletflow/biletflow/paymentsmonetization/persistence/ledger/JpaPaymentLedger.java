package com.biletflow.biletflow.paymentsmonetization.persistence.ledger;

import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentLedger;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.persistence.ledger.entity.LedgerEntryJpaEntity;
import com.biletflow.biletflow.paymentsmonetization.persistence.ledger.entity.LedgerEntryKind;
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
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void charge(PaymentWork work) {
        post(work, LedgerEntryKind.CHARGE, work.getFee());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refund(PaymentWork work, UUID refundId) {
        post(work, LedgerEntryKind.REFUND, BigDecimal.ZERO);
    }

    private void post(PaymentWork work, LedgerEntryKind kind, BigDecimal fee) {
        locks.lock("ledger:" + work.getId());
        String key = work.getId() + ":" + kind.name().toLowerCase(Locale.ROOT);
        if (entities.find(LedgerEntryJpaEntity.class, key) != null) return;
        entities.persist(
            new LedgerEntryJpaEntity(
                key,
                work.getId(),
                work.getEventId(),
                work.getOrganizerId(),
                kind,
                work.getAmount(),
                fee,
                clock.instant()
            )
        );
    }

    @Override
    public Map<String, BigDecimal> summary(long organizer) {
        Object[] totals = entities
            .createQuery(
                "select coalesce(sum(case when l.kind = :charge then l.amount else 0 end),0), coalesce(sum(l.fee),0), coalesce(sum(case when l.kind = :refund then l.amount else 0 end),0) from LedgerEntryJpaEntity l where l.organizerId = :organizer",
                Object[].class
            )
            .setParameter("charge", LedgerEntryKind.CHARGE)
            .setParameter("refund", LedgerEntryKind.REFUND)
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
