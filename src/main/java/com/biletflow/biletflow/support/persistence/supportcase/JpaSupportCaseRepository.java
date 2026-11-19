package com.biletflow.biletflow.support.persistence.supportcase;

import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.SupportCase;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseId;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseRepository;
import com.biletflow.biletflow.support.persistence.supportcase.entity.SupportCaseJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaSupportCaseRepository implements SupportCaseRepository {

    private final SpringDataSupportCaseJpaRepository jpaRepository;
    private final SupportCasePersistenceMapper mapper;
    private final EntityManager entityManager;

    public JpaSupportCaseRepository(
        SpringDataSupportCaseJpaRepository jpaRepository,
        SupportCasePersistenceMapper mapper,
        EntityManager entityManager
    ) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    @Override
    @Transactional
    public SupportCase save(SupportCase supportCase) {
        Objects.requireNonNull(supportCase, "supportCase cannot be null");

        SupportCaseJpaEntity managed = entityManager.find(SupportCaseJpaEntity.class, supportCase.getId().value());
        if (managed == null) {
            SupportCaseJpaEntity created = mapper.toNewEntity(supportCase);
            entityManager.persist(created);
            entityManager.flush();
            return mapper.toDomain(created);
        }

        mapper.updateEntity(supportCase, managed);
        entityManager.flush();
        return mapper.toDomain(managed);
    }

    @Override
    public Optional<SupportCase> findById(SupportCaseId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<SupportCase> findByRequesterUserId(Long requesterUserId) {
        Objects.requireNonNull(requesterUserId, "requesterUserId cannot be null");
        return jpaRepository.findAllByRequesterUserId(requesterUserId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<SupportCase> findByAssignedSupportUserId(Long supportUserId) {
        Objects.requireNonNull(supportUserId, "supportUserId cannot be null");
        return jpaRepository.findAllByAssignedSupportUserId(supportUserId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<SupportCase> findByStatus(CaseStatus status) {
        Objects.requireNonNull(status, "status cannot be null");
        return jpaRepository.findAllByStatus(status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<SupportCase> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }
}
