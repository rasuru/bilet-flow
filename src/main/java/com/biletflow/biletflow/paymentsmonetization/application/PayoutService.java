package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.*;
import com.biletflow.biletflow.paymentsmonetization.application.command.RequestPayoutCommand;
import com.biletflow.biletflow.paymentsmonetization.application.view.*;
import com.biletflow.biletflow.paymentsmonetization.domain.payout.*;
import com.biletflow.biletflow.paymentsmonetization.domain.revenue.OrganizerBalanceRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PayoutService {

    private final PayoutRepository payouts;
    private final OrganizerBalanceRepository balances;
    private final TransactionLocks locks;
    private final FinancialAudit audit;
    private final Clock clock;

    public PayoutService(
        PayoutRepository payouts,
        OrganizerBalanceRepository balances,
        TransactionLocks locks,
        FinancialAudit audit,
        Clock clock
    ) {
        this.payouts = Objects.requireNonNull(payouts);
        this.balances = Objects.requireNonNull(balances);
        this.locks = Objects.requireNonNull(locks);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public PayoutView request(RequestPayoutCommand command) {
        // Request identity before organizer: concurrent reuse cannot create two payouts, even across users.
        locks.lock("payout-request:" + command.requestId());
        locks.lock("organizer-funds:" + command.organizerId());
        var id = new PayoutId(command.requestId());
        var existing = payouts.findById(id);
        if (existing.isPresent()) {
            var payout = owned(existing.orElseThrow(), command.organizerId());
            payout.requireSameAmount(command.amount());
            return PayoutView.from(payout);
        }
        var balance = balances.get(command.organizerId());
        balance.requirePayoutAmount(command.amount());
        var payout = Payout.request(id, command.organizerId(), command.amount(), clock.instant());
        payouts.save(payout);
        audit.record("payments", id.value(), command.organizerId(), "PAYOUT_REQUESTED_SIMULATED");
        return PayoutView.from(payout);
    }

    public PayoutBalanceView balance(long organizerId) {
        locks.lock("organizer-funds:" + organizerId);
        return PayoutBalanceView.from(balances.get(organizerId));
    }

    public PayoutView get(UUID id, long organizerId) {
        return PayoutView.from(owned(require(new PayoutId(id)), organizerId));
    }

    public List<PayoutView> list(long organizerId, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid payout pagination");
        return payouts.findAllByOrganizerId(organizerId, offset, limit).stream().map(PayoutView::from).toList();
    }

    @Transactional
    public void process(PayoutId id, boolean simulatedSuccess) {
        var initial = require(id);
        locks.lock("organizer-funds:" + initial.getOrganizerId());
        var payout = require(id);
        if (payout.getStatus() != PayoutStatus.REQUESTED) return;
        payout.simulate(balances.get(payout.getOrganizerId()), simulatedSuccess, clock.instant());
        payouts.save(payout);
        audit.record("payments", id.value(), null, "PAYOUT_" + payout.getStatus().name() + "_SIMULATED");
    }

    private Payout require(PayoutId id) {
        return payouts.findById(id).orElseThrow(() -> new EntityNotFoundException("Payout not found"));
    }

    private Payout owned(Payout payout, long organizerId) {
        if (payout.getOrganizerId() != organizerId) throw new AuthorizationException("Payout is private");
        return payout;
    }
}
