package com.biletflow.biletflow.paymentsmonetization.persistence.settings;

import com.biletflow.biletflow.paymentsmonetization.application.PaymentSettingsRepository;
import com.biletflow.biletflow.paymentsmonetization.persistence.settings.entity.PaymentSettingJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaPaymentSettingsRepository implements PaymentSettingsRepository {

    static final String ACTIVATION_FEE = "activation-fee-kzt";

    private final EntityManager entities;

    public JpaPaymentSettingsRepository(EntityManager entities) {
        this.entities = Objects.requireNonNull(entities, "entities cannot be null");
    }

    @Override
    public Optional<ActivationFeeSetting> activationFee() {
        return Optional.ofNullable(entities.find(PaymentSettingJpaEntity.class, ACTIVATION_FEE)).map(row ->
            new ActivationFeeSetting(row.getAmountKzt(), row.getUpdatedBy(), row.getUpdatedAt())
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveActivationFee(ActivationFeeSetting setting) {
        var row = entities.find(PaymentSettingJpaEntity.class, ACTIVATION_FEE);
        if (row == null) entities.persist(
            new PaymentSettingJpaEntity(ACTIVATION_FEE, setting.amountKzt(), setting.updatedBy(), setting.updatedAt())
        );
        else row.update(setting.amountKzt(), setting.updatedBy(), setting.updatedAt());
    }
}
