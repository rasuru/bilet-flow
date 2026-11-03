package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.paymentsmonetization.application.view.ActivationFeeView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator-managed payment settings. Only administrators may read or change them. */
@Service
@Transactional(readOnly = true)
public class PaymentSettingsService {

    /** Stable audit aggregate ID for the platform activation-fee setting. */
    static final UUID ACTIVATION_FEE_SETTING = UUID.nameUUIDFromBytes("payments:activation-fee".getBytes(StandardCharsets.UTF_8));

    private final PaymentSettingsRepository settings;
    private final ActivationTermsProvider activationTerms;
    private final PaymentGateway bank;
    private final TransactionLocks locks;
    private final FinancialAudit audit;
    private final CurrentActor actor;
    private final Clock clock;

    public PaymentSettingsService(
        PaymentSettingsRepository settings,
        ActivationTermsProvider activationTerms,
        PaymentGateway bank,
        TransactionLocks locks,
        FinancialAudit audit,
        CurrentActor actor,
        Clock clock
    ) {
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
        this.activationTerms = Objects.requireNonNull(activationTerms, "activationTerms cannot be null");
        this.bank = Objects.requireNonNull(bank, "bank cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.actor = Objects.requireNonNull(actor, "actor cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public ActivationFeeView activationFee() {
        actor.requireAdministrator();
        return view();
    }

    /**
     * Changes the fee for activation requests made from now on. Unpaid attempts already created keep the amount they
     * were issued with, and fees already paid are unaffected.
     */
    @Transactional
    public ActivationFeeView changeActivationFee(BigDecimal amountKzt) {
        Objects.requireNonNull(amountKzt, "amountKzt cannot be null");
        long admin = actor.requireAdministrator();

        locks.lock("payment-settings:activation-fee");
        var current = activationTerms.current();
        var validated = current.withActivationFee(amountKzt).activationFee();
        bank.validateAmount(validated);
        settings.saveActivationFee(
            new PaymentSettingsRepository.ActivationFeeSetting(validated.setScale(2, RoundingMode.UNNECESSARY), admin, clock.instant())
        );
        audit.recordDetails(
            "payments",
            ACTIVATION_FEE_SETTING,
            admin,
            "ACTIVATION_FEE_CHANGED",
            current.activationFee().toPlainString() + " -> " + validated.toPlainString() + " KZT"
        );
        return view();
    }

    private ActivationFeeView view() {
        var override = settings.activationFee();
        return new ActivationFeeView(
            activationTerms.current().activationFee().setScale(2, RoundingMode.UNNECESSARY),
            "KZT",
            override.isPresent(),
            override.map(PaymentSettingsRepository.ActivationFeeSetting::updatedBy).orElse(null),
            override.map(PaymentSettingsRepository.ActivationFeeSetting::updatedAt).orElse(null),
            true
        );
    }
}
