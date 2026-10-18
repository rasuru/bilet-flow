package com.biletflow.biletflow.paymentsmonetization.persistence;

import com.biletflow.biletflow.paymentsmonetization.application.ActivationRepository;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.*;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePaymentId;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true)
public class JpaActivationRepository implements ActivationRepository {

    private final EntityManager entities;

    public JpaActivationRepository(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public Optional<Activation> find(UUID id) {
        var row = entities.find(ActivationJpaEntity.class, id);
        if (row == null) return Optional.empty();
        entities.flush();
        entities.refresh(row);
        return Optional.of(
            new Activation(
                new EventPaidSalesActivation(
                    row.eventId,
                    row.organizerId,
                    row.createdAt,
                    EventPaidSalesActivationStatus.valueOf(row.status),
                    row.feePaymentId == null ? null : new ActivationFeePaymentId(row.feePaymentId),
                    row.suspendedAt,
                    row.suspensionReason
                ),
                row.paymentId,
                row.verificationSimulated,
                row.termsVersion
            )
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Activation activation) {
        var domain = activation.aggregate();
        var row = entities.find(ActivationJpaEntity.class, domain.getEventId());
        boolean created = row == null;
        if (created) row = new ActivationJpaEntity();
        row.eventId = domain.getEventId();
        row.organizerId = domain.getOrganizerId();
        row.status = domain.getStatus().name();
        row.paymentId = activation.paymentId();
        row.feePaymentId = domain.getFeePaymentId() == null ? null : domain.getFeePaymentId().value();
        row.createdAt = domain.getCreatedAt();
        row.verificationSimulated = activation.verificationSimulated();
        row.termsVersion = activation.termsVersion();
        row.suspendedAt = domain.getSuspendedAt();
        row.suspensionReason = domain.getSuspensionReason();
        if (created) entities.persist(row);
    }
}
