package com.biletflow.biletflow.paymentsmonetization.persistence.payout;

import com.biletflow.biletflow.paymentsmonetization.application.PayoutNoticeStore;
import com.biletflow.biletflow.paymentsmonetization.persistence.payout.entity.PayoutJpaEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaPayoutNoticeStore implements PayoutNoticeStore {

    private final EntityManager entities;

    public JpaPayoutNoticeStore(EntityManager entities) {
        this.entities = Objects.requireNonNull(entities);
    }

    @Override
    public Optional<Claim> claim(Instant now) {
        return entities
            .createQuery(
                "select p from PayoutJpaEntity p where p.noticeSentAt is null and p.noticeNextAttemptAt <= :now " +
                    "and (p.noticeLeaseUntil is null or p.noticeLeaseUntil <= :now) order by p.noticeNextAttemptAt, p.id",
                PayoutJpaEntity.class
            )
            .setParameter("now", now)
            .setMaxResults(1)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList()
            .stream()
            .findFirst()
            .map(row -> row.claimNotice(now));
    }

    @Override
    public void complete(Claim claim, Instant now) {
        var row = entities.find(PayoutJpaEntity.class, claim.payout().id(), LockModeType.PESSIMISTIC_WRITE);
        if (row != null) row.completeNotice(claim, now);
    }

    @Override
    public void retry(Claim claim, Instant now) {
        var row = entities.find(PayoutJpaEntity.class, claim.payout().id(), LockModeType.PESSIMISTIC_WRITE);
        if (row != null) row.retryNotice(claim, now);
    }
}
