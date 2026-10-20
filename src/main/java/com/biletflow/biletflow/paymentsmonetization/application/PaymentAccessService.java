package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.CallbackSecrets;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.paymentsmonetization.application.command.ResolvePaymentOperationCommand;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentAccessService {

    private final PaymentWorkRepository works;
    private final CallbackSecrets secrets;
    private final PaymentGateway bank;
    private final PaymentTransitions transitions;
    private final FinancialAudit audit;
    private final Clock clock;
    private final TransactionLocks locks;

    public PaymentAccessService(
        PaymentWorkRepository works,
        PaymentGateway bank,
        CallbackSecrets secrets,
        PaymentTransitions transitions,
        FinancialAudit audit,
        Clock clock,
        TransactionLocks locks
    ) {
        this.works = Objects.requireNonNull(works, "works cannot be null");
        this.secrets = Objects.requireNonNull(secrets, "secrets cannot be null");
        this.bank = Objects.requireNonNull(bank, "bank cannot be null");
        this.transitions = Objects.requireNonNull(transitions, "transitions cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.locks = Objects.requireNonNull(locks);
    }

    public void callback(String invoice, String secret) {
        if (!bank.acceptsInvoiceId(invoice)) throw denied();
        var work = works.findInvoice(invoice).orElseThrow(PaymentAccessService::denied);
        if (!secrets.matches(work.getCallbackSecret(), secret)) throw denied();
        // Authentication grants permission to request reconciliation, never to change financial state.
        locks.lock("payment:" + work.getId());
        works.wake(work.getId(), clock.instant());
    }

    public void resolve(ResolvePaymentOperationCommand command) {
        UUID id = command.paymentId();
        long actor = command.actorId();
        UUID expectedOperation = command.expectedOperationId();
        var resolution = command.resolution();
        String evidence = command.bankSupportReference();
        transitions.resolve(id, expectedOperation, resolution);
        works.wake(id, clock.instant());
        audit.recordDetails("payments", id, actor, "OPERATOR_" + resolution, evidence);
    }

    private static AuthorizationException denied() {
        return new AuthorizationException("Invalid callback authentication");
    }
}
