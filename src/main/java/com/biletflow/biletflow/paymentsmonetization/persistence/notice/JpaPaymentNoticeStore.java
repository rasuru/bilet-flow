package com.biletflow.biletflow.paymentsmonetization.persistence.notice;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentNoticeStore;
import com.biletflow.biletflow.paymentsmonetization.persistence.notice.entity.PaymentNoticeJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaPaymentNoticeStore implements PaymentNoticeStore {

    private final EntityManager entities;

    public JpaPaymentNoticeStore(EntityManager entities) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(PaymentNotice notice) {
        // Notice IDs are deterministic and the payment transition that enqueues them holds the payment lock.
        if (entities.find(PaymentNoticeJpaEntity.class, notice.id()) == null) entities.persist(new PaymentNoticeJpaEntity(notice));
    }

    @Override
    public Optional<Claim> claim(Instant now) {
        return entities
            .createQuery(
                "select n from PaymentNoticeJpaEntity n where n.sentAt is null and n.nextAttemptAt <= :now " +
                    "and (n.leaseUntil is null or n.leaseUntil <= :now) order by n.nextAttemptAt, n.id",
                PaymentNoticeJpaEntity.class
            )
            .setParameter("now", now)
            .setMaxResults(1)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList()
            .stream()
            .findFirst()
            .map(row -> row.claim(now));
    }

    @Override
    public void complete(Claim claim, Instant now) {
        var row = entities.find(PaymentNoticeJpaEntity.class, claim.notice().id(), LockModeType.PESSIMISTIC_WRITE);
        if (row != null) row.complete(claim, now);
    }

    @Override
    public void retry(Claim claim, Instant now) {
        var row = entities.find(PaymentNoticeJpaEntity.class, claim.notice().id(), LockModeType.PESSIMISTIC_WRITE);
        if (row != null) row.retry(claim, now);
    }
}
