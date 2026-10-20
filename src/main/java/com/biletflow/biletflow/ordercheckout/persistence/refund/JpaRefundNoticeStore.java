package com.biletflow.biletflow.ordercheckout.persistence.refund;

import com.biletflow.biletflow.ordercheckout.application.refund.*;
import com.biletflow.biletflow.ordercheckout.persistence.refund.entity.RefundNoticeJpaEntity;
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
public class JpaRefundNoticeStore implements RefundNoticeStore {

    private final EntityManager entities;

    public JpaRefundNoticeStore(EntityManager entities) {
        this.entities = Objects.requireNonNull(entities);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(RefundNotice notice) {
        if (entities.find(RefundNoticeJpaEntity.class, notice.id()) == null) {
            entities.persist(new RefundNoticeJpaEntity(notice));
        }
    }

    @Override
    public Optional<Claim> claim(Instant now) {
        return entities
            .createQuery(
                "select n from RefundNoticeJpaEntity n where n.sentAt is null and n.nextAttemptAt <= :now " +
                    "and (n.leaseUntil is null or n.leaseUntil <= :now) order by n.nextAttemptAt, n.id",
                RefundNoticeJpaEntity.class
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
        var row = entities.find(RefundNoticeJpaEntity.class, claim.notice().id(), LockModeType.PESSIMISTIC_WRITE);
        if (row != null) row.complete(claim, now);
    }

    @Override
    public void retry(Claim claim, Instant now) {
        var row = entities.find(RefundNoticeJpaEntity.class, claim.notice().id(), LockModeType.PESSIMISTIC_WRITE);
        if (row != null) row.retry(claim, now);
    }
}
