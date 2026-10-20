package com.biletflow.biletflow.paymentsmonetization.persistence.activation;

import com.biletflow.biletflow.paymentsmonetization.application.ActivationRepository;
import com.biletflow.biletflow.paymentsmonetization.persistence.activation.entity.ActivationJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaActivationRepository implements ActivationRepository {

    private final EntityManager entities;
    private final ActivationPersistenceMapper mapper;

    public JpaActivationRepository(EntityManager entities, ActivationPersistenceMapper mapper) {
        this.entities = Objects.requireNonNull(entities);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<Activation> find(UUID id) {
        var row = entities.find(ActivationJpaEntity.class, id);
        if (row == null) return Optional.empty();
        entities.flush();
        entities.refresh(row);
        return Optional.of(mapper.toDomain(row));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Activation activation) {
        var row = entities.find(ActivationJpaEntity.class, activation.aggregate().getEventId());
        if (row == null) entities.persist(mapper.toNewEntity(activation));
        else mapper.updateEntity(activation, row);
    }
}
