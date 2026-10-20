package com.biletflow.biletflow.ordercheckout.persistence.checkout;

import com.biletflow.biletflow.ordercheckout.application.CheckoutProcess;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutHoldJpaEmbeddable;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutLineJpaEmbeddable;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutProcessJpaEntity;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CheckoutProcessPersistenceMapper {

    public CheckoutProcessJpaEntity toNewEntity(CheckoutProcess domain) {
        return new CheckoutProcessJpaEntity(
            domain.id(),
            domain.eventId(),
            domain.ownerId(),
            domain.fingerprint(),
            domain.deadline(),
            domain.paymentId(),
            domain.refundState(),
            domain.refundId(),
            domain.lines().stream().map(CheckoutLineJpaEmbeddable::new).toList(),
            domain.holds().stream().map(CheckoutHoldJpaEmbeddable::new).toList()
        );
    }

    public void updateEntity(CheckoutProcess domain, CheckoutProcessJpaEntity entity) {
        if (!entity.getId().equals(domain.id())) throw new IllegalArgumentException("Cannot map onto another CheckoutProcess id");
        entity.updateMutableState(domain.paymentId(), domain.refundState(), domain.refundId());
    }

    public CheckoutProcess toDomain(CheckoutProcessJpaEntity entity) {
        return new CheckoutProcess(
            entity.getId(),
            entity.getOwnerId(),
            entity.getEventId(),
            entity.getFingerprint(),
            entity.getDeadline(),
            entity.getLines().stream().map(CheckoutLineJpaEmbeddable::toDomain).toList(),
            entity.getHolds().stream().map(CheckoutHoldJpaEmbeddable::toDomain).toList(),
            entity.getPaymentId(),
            entity.getRefundState(),
            entity.getRefundId()
        );
    }
}
