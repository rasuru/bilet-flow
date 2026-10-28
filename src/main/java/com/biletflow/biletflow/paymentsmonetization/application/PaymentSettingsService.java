package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.paymentsmonetization.application.view.ActivationFeeView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator-managed payment settings. Endpoint security restricts changes to administrators. */
@Service
@Transactional(readOnly = true)
public class PaymentSettingsService {

    /** Stable audit aggregate ID for the platform activation-fee setting. */
    static final UUID ACTIVATION_FEE_SETTING = UUID.nameUUIDFromBytes("payments:activation-fee".getBytes(StandardCharsets.UTF_8));

    private final PaymentSettingsRepository settings;
    private final PaymentService payments;
    private final PaymentGateway bank;
    private final TransactionLocks locks;
    private final FinancialAudit audit;
    private final Clock clock;

    public PaymentSettingsService(
        PaymentSettingsRepository settings,
        PaymentService payments,
        PaymentGateway bank,
        TransactionLocks locks,
        FinancialAudit audit,
        Clock clock
    ) {
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
        this.payments = Objects.requireNonNull(payments, "payments cannot be null");
        this.bank = Objects.requireNonNull(bank, "bank cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public ActivationFeeView activationFee() {
        var current = settings.activationFee();
        return new ActivationFeeView(
            payments.activationTerms().activationFee().setScale(2, RoundingMode.UNNECESSARY),
            "KZT",
            current.isPresent(),
            current.map(PaymentSettingsRepository.ActivationFeeSetting::updatedBy).orElse(null),
            current.map(PaymentSettingsRepository.ActivationFeeSetting::updatedAt).orElse(null),
            true
        );
    }

    /**
     * Changes the fee for activation requests made from now on. Unpaid attempts already created keep the amount they
     * were issued with, and fees already paid are unaffected.
     */
    @Transactional
    public ActivationFeeView changeActivationFee(BigDecimal amountKzt, long actorId) {
        Objects.requireNonNull(amountKzt, "amountKzt cannot be null");
        var validated = payments.activationTerms().withActivationFee(amountKzt).activationFee();
        bank.validateAmount(validated);
        locks.lock("payment-settings:activation-fee");
        var previous = payments.activationTerms().activationFee();
        settings.saveActivationFee(
            new PaymentSettingsRepository.ActivationFeeSetting(validated.setScale(2, RoundingMode.UNNECESSARY), actorId, clock.instant())
        );
        audit.recordDetails(
            "payments",
            ACTIVATION_FEE_SETTING,
            actorId,
            "ACTIVATION_FEE_CHANGED",
            previous.toPlainString() + " -> " + validated.toPlainString() + " KZT"
        );
        return activationFee();
    }
}
