package com.biletflow.biletflow.paymentsmonetization.persistence.payout;

import com.biletflow.biletflow.paymentsmonetization.domain.payout.*;
import com.biletflow.biletflow.paymentsmonetization.persistence.payout.entity.PayoutJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaPayoutRepository implements PayoutRepository {

    private final EntityManager entities;
    private final PayoutPersistenceMapper mapper;

    public JpaPayoutRepository(EntityManager entities, PayoutPersistenceMapper mapper) {
        this.entities = Objects.requireNonNull(entities);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<Payout> findById(PayoutId id) {
        var row = entities.find(PayoutJpaEntity.class, id.value());
        if (row == null) return Optional.empty();
        entities.flush();
        entities.refresh(row);
        return Optional.of(mapper.toDomain(row));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payout save(Payout payout) {
        var row = entities.find(PayoutJpaEntity.class, payout.getId().value());
        if (row == null) entities.persist(mapper.toNewEntity(payout));
        else mapper.updateEntity(payout, row);
        return payout;
    }

    @Override
    public List<Payout> findAllByOrganizerId(Long organizerId, int offset, int limit) {
        return entities
            .createQuery(
                "select p from PayoutJpaEntity p where p.organizerId = :organizer order by p.requestedAt desc, p.id desc",
                PayoutJpaEntity.class
            )
            .setParameter("organizer", organizerId)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList()
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<PayoutId> pending(int limit) {
        return entities
            .createQuery("select p.id from PayoutJpaEntity p where p.status = :status order by p.requestedAt, p.id", UUID.class)
            .setParameter("status", PayoutStatus.REQUESTED)
            .setMaxResults(limit)
            .getResultList()
            .stream()
            .map(PayoutId::new)
            .toList();
    }
}
