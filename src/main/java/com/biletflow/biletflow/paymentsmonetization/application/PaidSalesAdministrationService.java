package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.paymentsmonetization.application.command.SuspendPaidSalesCommand;
import com.biletflow.biletflow.paymentsmonetization.application.view.ActivationRecordView;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-administrator control over an event's paid sales (SRS 4.5). Only administrators may use this use case;
 * the acting administrator and reason are audited.
 */
@Service
@Transactional
public class PaidSalesAdministrationService {

    private final ActivationRepository activations;
    private final TransactionLocks locks;
    private final FinancialAudit audit;
    private final CurrentActor actor;
    private final Clock clock;

    public PaidSalesAdministrationService(
        ActivationRepository activations,
        TransactionLocks locks,
        FinancialAudit audit,
        CurrentActor actor,
        Clock clock
    ) {
        this.activations = Objects.requireNonNull(activations, "activations cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.actor = Objects.requireNonNull(actor, "actor cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /** New paid checkouts are rejected immediately. Payments already started still settle and issued tickets stay valid. */
    public ActivationRecordView suspend(SuspendPaidSalesCommand command) {
        long admin = actor.requireAdministrator();
        locks.lock("activation:" + command.eventId());
        var activation = activations.find(command.eventId()).orElseThrow(() -> new EntityNotFoundException("Activation not found"));
        activation.aggregate().suspend(command.reason(), clock.instant());
        activations.save(activation);
        audit.recordDetails("payments", command.eventId(), admin, "PAID_SALES_SUSPENDED", command.reason());
        return ActivationRecordView.from(activation);
    }
}
