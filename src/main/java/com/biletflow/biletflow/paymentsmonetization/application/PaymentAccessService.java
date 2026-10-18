package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.CallbackSecrets;
import com.biletflow.biletflow.common.application.FinancialAudit;
import java.time.Clock;
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

    public PaymentAccessService(
        PaymentWorkRepository works,
        PaymentGateway bank,
        CallbackSecrets secrets,
        PaymentTransitions transitions,
        FinancialAudit audit,
        Clock clock
    ) {
        this.works = works;
        this.secrets = secrets;
        this.bank = bank;
        this.transitions = transitions;
        this.audit = audit;
        this.clock = clock;
    }

    public void callback(String invoice, String secret) {
        if (!bank.acceptsInvoiceId(invoice)) throw denied();
        var work = works.findInvoice(invoice).orElseThrow(PaymentAccessService::denied);
        if (!secrets.matches(work.callbackSecret, secret)) throw denied();
        // Authentication grants permission to request reconciliation, never to change financial state.
        works.wake(work.id, clock.instant());
    }

    public void resolve(UUID id, long actor, UUID expectedOperation, String resolution, String evidence) {
        if (evidence == null || evidence.isBlank() || evidence.length() > 200) throw new IllegalArgumentException(
            "Bank support reference is required (max 200 characters)"
        );
        transitions.resolve(id, expectedOperation, resolution);
        works.wake(id, clock.instant());
        audit.recordDetails("payments", id, actor, "OPERATOR_" + resolution, evidence);
    }

    private static org.springframework.security.access.AccessDeniedException denied() {
        return new org.springframework.security.access.AccessDeniedException("Invalid callback authentication");
    }
}
