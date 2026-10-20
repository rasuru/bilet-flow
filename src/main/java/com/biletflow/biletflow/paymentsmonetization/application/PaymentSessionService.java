package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Coordinates a durable claim, provider call outside a transaction, and atomic publication. */
@Service
public class PaymentSessionService {

    private final PaymentSessionStore sessions;
    private final PaymentGateway bank;
    private final Clock clock;

    public PaymentSessionService(PaymentSessionStore sessions, PaymentGateway bank, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.bank = Objects.requireNonNull(bank, "bank cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public PaymentGateway.Session open(PaymentWork work) {
        work.requireSessionAvailable(clock.instant());
        UUID claim = UUID.randomUUID();
        var cached = sessions.claim(work.getId(), claim, clock.instant());
        if (cached != null) return cached;
        try {
            var session = bank.session(work);
            sessions.complete(work.getId(), claim, session, work.getDeadline(), clock.instant());
            return session;
        } catch (RuntimeException failure) {
            sessions.release(work.getId(), claim);
            throw failure;
        }
    }
}
