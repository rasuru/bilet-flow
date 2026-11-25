package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.paymentsmonetization.application.MockPaymentScenarios.Scenario;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentProvider;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentStatus;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MockCheckoutService {

    private static final long DELAY_SECONDS = 10;
    private final PaymentWorkRepository works;
    private final MockPaymentScenarios scenarios;
    private final TransactionLocks locks;
    private final FinancialAudit audit;
    private final Clock clock;

    public MockCheckoutService(
        PaymentWorkRepository works,
        MockPaymentScenarios scenarios,
        TransactionLocks locks,
        FinancialAudit audit,
        Clock clock
    ) {
        this.works = works;
        this.scenarios = scenarios;
        this.locks = locks;
        this.audit = audit;
        this.clock = clock;
    }

    public CheckoutView get(UUID id, long payer) {
        var work = requireMock(id, payer);
        var attempt = scenarios.find(id).orElse(null);
        Instant now = clock.instant();
        String failure = work.isTicket() ? work.getPayment().getFailureReason() : work.getActivationPayment().getFailureReason();
        boolean expired =
            "PAYMENT_EXPIRED".equals(failure) || (work.getStatus() == PaymentStatus.PENDING && !now.isBefore(work.getDeadline()));
        return new CheckoutView(
            work.getAmount(),
            work.getDeadline(),
            work.isTicket() ? work.getOrderId() : null,
            work.getEventId(),
            work.getStatus(),
            attempt == null ? null : attempt.scenario(),
            attempt == null && work.getStatus() == PaymentStatus.PENDING && !work.isAbandoned() && now.isBefore(work.getDeadline()),
            work.isAbandoned() || expired,
            expired,
            now.plusSeconds(DELAY_SECONDS).isBefore(work.getDeadline()),
            DELAY_SECONDS
        );
    }

    @Transactional
    public void choose(UUID id, long payer, Scenario scenario) {
        locks.lock("payment:" + id);
        var work = requireMock(id, payer);
        var previous = scenarios.find(id);
        if (previous.isPresent()) {
            if (previous.orElseThrow().scenario() != scenario) throw new IllegalStateException(
                "This payment already has a simulated outcome"
            );
            return;
        }

        Instant now = clock.instant();
        work.requireSessionAvailable(now);
        var settlesAt = scenario == Scenario.DELAYED_SUCCESS ? now.plusSeconds(DELAY_SECONDS) : now;
        if (scenario == Scenario.DELAYED_SUCCESS && !settlesAt.isBefore(work.getDeadline())) {
            throw new IllegalStateException("This payment expires before the simulated delay finishes");
        }
        scenarios.insert(id, scenario, settlesAt);
        works.wake(id, now);
        audit.recordDetails("payments", id, payer, "MOCK_PAYMENT_SCENARIO_SELECTED", scenario.name());
    }

    private PaymentWork requireMock(UUID id, long payer) {
        var work = works.find(id).orElseThrow(() -> new EntityNotFoundException("Payment not found"));
        if (work.getPayerId() != payer) throw new AuthorizationException("Only the payer can simulate this payment");
        if (PaymentProvider.forInvoice(work.getInvoiceId()) != PaymentProvider.MOCK) {
            throw new EntityNotFoundException("Mock payment not found");
        }
        return work;
    }

    public record CheckoutView(
        BigDecimal amountKzt,
        Instant expiresAt,
        UUID orderId,
        UUID eventId,
        PaymentStatus status,
        Scenario scenario,
        boolean canChoose,
        boolean closed,
        boolean expired,
        boolean canDelay,
        long delaySeconds
    ) {}
}
