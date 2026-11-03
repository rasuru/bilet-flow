package com.biletflow.biletflow.paymentsmonetization.persistence.admin;

import com.biletflow.biletflow.paymentsmonetization.application.ActivationRepository;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentAdminQueries;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivationStatus;
import com.biletflow.biletflow.paymentsmonetization.persistence.activation.ActivationPersistenceMapper;
import com.biletflow.biletflow.paymentsmonetization.persistence.activation.entity.ActivationJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaPaymentAdminQueries implements PaymentAdminQueries {

    private final EntityManager entities;
    private final ActivationPersistenceMapper activationMapper;

    public JpaPaymentAdminQueries(EntityManager entities, ActivationPersistenceMapper activationMapper) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
        this.activationMapper = Objects.requireNonNull(activationMapper, "activationMapper cannot be null");
    }

    @Override
    public List<UUID> findPaymentIds(PaymentFilter filter, int offset, int limit) {
        var where = new ArrayList<String>();
        var parameters = new HashMap<String, Object>();
        if (filter.status() != null) {
            where.add("p.status = :status");
            parameters.put("status", filter.status());
        }
        if (filter.purpose() != null) {
            where.add("p.purpose = :purpose");
            parameters.put("purpose", filter.purpose());
        }
        if (filter.eventId() != null) {
            where.add("p.eventId = :eventId");
            parameters.put("eventId", filter.eventId());
        }
        if (filter.payerId() != null) {
            where.add("p.payerId = :payerId");
            parameters.put("payerId", filter.payerId());
        }
        String jpql =
            "select p.id from PaymentWorkJpaEntity p" +
            (where.isEmpty() ? "" : " where " + String.join(" and ", where)) +
            " order by p.deadline desc, p.id";
        var query = entities.createQuery(jpql, UUID.class).setFirstResult(offset).setMaxResults(limit);
        for (var parameter : parameters.entrySet()) query.setParameter(parameter.getKey(), parameter.getValue());
        return query.getResultList();
    }

    @Override
    public List<ActivationRepository.Activation> findActivations(EventPaidSalesActivationStatus status, int offset, int limit) {
        String jpql =
            "select a from ActivationJpaEntity a" +
            (status == null ? "" : " where a.status = :status") +
            " order by a.createdAt desc, a.eventId";
        var query = entities.createQuery(jpql, ActivationJpaEntity.class).setFirstResult(offset).setMaxResults(limit);
        if (status != null) query.setParameter("status", status);
        return query.getResultList().stream().map(activationMapper::toDomain).toList();
    }
}
