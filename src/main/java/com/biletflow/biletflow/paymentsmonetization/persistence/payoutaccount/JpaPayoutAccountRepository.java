package com.biletflow.biletflow.paymentsmonetization.persistence.payoutaccount;

import com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount.PayoutAccount;
import com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount.PayoutAccountRepository;
import com.biletflow.biletflow.paymentsmonetization.persistence.payoutaccount.entity.PayoutAccountJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaPayoutAccountRepository implements PayoutAccountRepository {

    private final EntityManager entities;

    public JpaPayoutAccountRepository(EntityManager entities) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
    }

    @Override
    public Optional<PayoutAccount> findByOrganizerId(long organizerId) {
        return Optional.ofNullable(entities.find(PayoutAccountJpaEntity.class, organizerId)).map(row ->
            new PayoutAccount(
                row.getOrganizerId(),
                row.getHolderName(),
                row.getBankCode(),
                row.getIbanLastFour(),
                row.getRegisteredAt(),
                row.getUpdatedAt()
            )
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PayoutAccount save(PayoutAccount account) {
        var row = entities.find(PayoutAccountJpaEntity.class, account.getOrganizerId());
        if (row == null) {
            row = new PayoutAccountJpaEntity(account.getOrganizerId(), account.getRegisteredAt());
            row.updateMutableState(account.getHolderName(), account.getBankCode(), account.getIbanLastFour(), account.getUpdatedAt());
            entities.persist(row);
        } else row.updateMutableState(account.getHolderName(), account.getBankCode(), account.getIbanLastFour(), account.getUpdatedAt());
        return account;
    }
}
