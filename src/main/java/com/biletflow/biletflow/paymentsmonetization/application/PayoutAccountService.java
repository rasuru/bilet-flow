package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.paymentsmonetization.application.command.RegisterPayoutAccountCommand;
import com.biletflow.biletflow.paymentsmonetization.application.view.PayoutAccountView;
import com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount.KzIban;
import com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount.PayoutAccount;
import com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount.PayoutAccountRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PayoutAccountService {

    private final PayoutAccountRepository accounts;
    private final TransactionLocks locks;
    private final FinancialAudit audit;
    private final Clock clock;

    public PayoutAccountService(PayoutAccountRepository accounts, TransactionLocks locks, FinancialAudit audit, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts cannot be null");
        this.locks = Objects.requireNonNull(locks, "locks cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public PayoutAccountView get(long organizerId) {
        return accounts
            .findByOrganizerId(organizerId)
            .map(PayoutAccountView::from)
            .orElseThrow(() -> new EntityNotFoundException("No payout account is registered"));
    }

    @Transactional
    public PayoutAccountView register(RegisterPayoutAccountCommand command) {
        var iban = KzIban.parse(command.iban());
        locks.lock("payout-account:" + command.organizerId());
        var existing = accounts.findByOrganizerId(command.organizerId());
        PayoutAccount account;
        String action;
        if (existing.isPresent()) {
            account = existing.orElseThrow();
            account.replace(command.holderName(), iban, clock.instant());
            action = "PAYOUT_ACCOUNT_REPLACED_SIMULATED";
        } else {
            account = PayoutAccount.register(command.organizerId(), command.holderName(), iban, clock.instant());
            action = "PAYOUT_ACCOUNT_REGISTERED_SIMULATED";
        }
        accounts.save(account);
        audit.recordDetails("payments", auditId(command.organizerId()), command.organizerId(), action, account.maskedIban());
        return PayoutAccountView.from(account);
    }

    static UUID auditId(long organizerId) {
        return UUID.nameUUIDFromBytes(("payments:payout-account:" + organizerId).getBytes(StandardCharsets.UTF_8));
    }
}
