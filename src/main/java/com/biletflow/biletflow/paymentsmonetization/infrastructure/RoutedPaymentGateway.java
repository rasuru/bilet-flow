package com.biletflow.biletflow.paymentsmonetization.infrastructure;

import com.biletflow.biletflow.paymentsmonetization.application.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.SettlementStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class RoutedPaymentGateway implements PaymentGateway {

    private final PaymentGateway halyk;
    private final PaymentSettingsRepository settings;
    private final PaymentWorkRepository works;
    private final MockPaymentScenarios mock;
    private final Clock clock;

    public RoutedPaymentGateway(
        @Qualifier("halykGateway") PaymentGateway halyk,
        PaymentSettingsRepository settings,
        PaymentWorkRepository works,
        MockPaymentScenarios mock,
        Clock clock
    ) {
        this.halyk = halyk;
        this.settings = settings;
        this.works = works;
        this.mock = mock;
        this.clock = clock;
    }

    @Override
    public String allocateInvoice(BigDecimal amount, Supplier<String> halykInvoice) {
        var provider = settings.paymentProvider().map(PaymentSettingsRepository.ProviderSetting::provider).orElse(PaymentProvider.HALYK);
        if (provider == PaymentProvider.MOCK) return mock.nextInvoice();
        return halyk.allocateInvoice(amount, halykInvoice);
    }

    @Override
    public boolean acceptsInvoiceId(String invoiceId) {
        return halyk.acceptsInvoiceId(invoiceId);
    }

    @Override
    public void validateAmount(BigDecimal amount) {
        // The shared activation fee must remain valid after switching back to Halyk.
        halyk.validateAmount(amount);
    }

    @Override
    public Session session(PaymentWork work) {
        return halyk.session(work);
    }

    @Override
    public SettlementEvidence inspect(String invoiceId) {
        if (PaymentProvider.forInvoice(invoiceId) == PaymentProvider.HALYK) return halyk.inspect(invoiceId);
        var work = works.findInvoice(invoiceId).orElseThrow();
        var now = clock.instant();
        var status = mock
            .find(work.getId())
            .map(attempt -> attempt.status(work, now))
            .orElseGet(() -> now.isBefore(work.getDeadline()) ? SettlementStatus.PENDING : SettlementStatus.EXPIRED);
        return new SettlementEvidence(
            invoiceId,
            work.getId().toString(),
            work.getAmount(),
            "KZT",
            true,
            status,
            work.getId().toString(),
            work.getAmount(),
            status == SettlementStatus.REFUNDED ? work.getAmount() : BigDecimal.ZERO
        );
    }

    @Override
    public void mutate(String transactionId, PaymentOperation operation, String externalId) {
        halyk.mutate(transactionId, operation, externalId);
    }

    @Override
    public void mutate(PaymentWork work, String transactionId, PaymentOperation operation, String externalId) {
        if (PaymentProvider.forInvoice(work.getInvoiceId()) == PaymentProvider.HALYK) {
            mutate(transactionId, operation, externalId);
            return;
        }
        if (operation != PaymentOperation.REFUND || !work.getId().toString().equals(transactionId)) {
            throw new IllegalStateException("Unsupported mock provider operation");
        }
        mock.markRefunded(work.getId());
    }
}
