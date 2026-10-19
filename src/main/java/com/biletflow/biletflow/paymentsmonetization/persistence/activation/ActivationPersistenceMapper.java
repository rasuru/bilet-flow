package com.biletflow.biletflow.paymentsmonetization.persistence.activation;

import com.biletflow.biletflow.paymentsmonetization.application.ActivationRepository.Activation;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.*;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.ActivationFeePaymentId;
import com.biletflow.biletflow.paymentsmonetization.persistence.activation.entity.ActivationJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ActivationPersistenceMapper {

    public Activation toDomain(ActivationJpaEntity row) {
        return new Activation(
            new EventPaidSalesActivation(
                row.getEventId(),
                row.getOrganizerId(),
                row.getCreatedAt(),
                row.getStatus(),
                row.getFeePaymentId() == null ? null : new ActivationFeePaymentId(row.getFeePaymentId()),
                row.getSuspendedAt(),
                row.getSuspensionReason()
            ),
            row.getPaymentId(),
            row.isVerificationSimulated(),
            row.getTermsVersion()
        );
    }

    public ActivationJpaEntity toNewEntity(Activation activation) {
        var domain = activation.aggregate();
        return new ActivationJpaEntity(
            domain.getEventId(),
            domain.getOrganizerId(),
            domain.getStatus(),
            activation.paymentId(),
            domain.getFeePaymentId() == null ? null : domain.getFeePaymentId().value(),
            domain.getCreatedAt(),
            activation.verificationSimulated(),
            activation.termsVersion(),
            domain.getSuspendedAt(),
            domain.getSuspensionReason()
        );
    }

    public void updateEntity(Activation activation, ActivationJpaEntity entity) {
        var domain = activation.aggregate();
        if (
            !entity.getEventId().equals(domain.getEventId()) || entity.getOrganizerId() != domain.getOrganizerId()
        ) throw new IllegalArgumentException("Cannot map onto another activation identity");
        entity.updateMutableState(
            domain.getStatus(),
            activation.paymentId(),
            domain.getFeePaymentId() == null ? null : domain.getFeePaymentId().value(),
            domain.getCreatedAt(),
            activation.verificationSimulated(),
            activation.termsVersion(),
            domain.getSuspendedAt(),
            domain.getSuspensionReason()
        );
    }
}
