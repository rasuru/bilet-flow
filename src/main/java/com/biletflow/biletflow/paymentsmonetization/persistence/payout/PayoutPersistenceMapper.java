package com.biletflow.biletflow.paymentsmonetization.persistence.payout;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.paymentsmonetization.domain.payout.*;
import com.biletflow.biletflow.paymentsmonetization.persistence.payout.entity.PayoutJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PayoutPersistenceMapper {

    public Payout toDomain(PayoutJpaEntity row) {
        return new Payout(
            new PayoutId(row.getId()),
            row.getOrganizerId(),
            Money.kzt(row.getAmountKzt()),
            row.getRequestedAt(),
            row.getStatus(),
            row.getProviderReference(),
            row.getFailureReason(),
            row.getCompletedAt()
        );
    }

    public PayoutJpaEntity toNewEntity(Payout payout) {
        var row = new PayoutJpaEntity(
            payout.getId().value(),
            payout.getOrganizerId(),
            payout.getAmount().amount(),
            payout.getRequestedAt()
        );
        updateEntity(payout, row);
        return row;
    }

    public void updateEntity(Payout payout, PayoutJpaEntity row) {
        row.updateMutableState(payout.getStatus(), payout.getProviderReference(), payout.getFailureReason(), payout.getCompletedAt());
    }
}
