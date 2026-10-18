package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Coordinates a durable claim, provider call outside a transaction, and atomic publication. */
@Service
public class PaymentSessionService {

    private final PaymentSessionStore sessions;
    private final PaymentGateway bank;
    private final Clock clock;

    public PaymentSessionService(PaymentSessionStore sessions, PaymentGateway bank, Clock clock) {
        this.sessions = sessions;
        this.bank = bank;
        this.clock = clock;
    }

    public PaymentGateway.Session open(PaymentWork work) {
        work.requireSessionAvailable(clock.instant());
        UUID claim = UUID.randomUUID();
        var cached = sessions.claim(work.id, claim, clock.instant());
        if (cached != null) return cached;
        try {
            var session = bank.session(work);
            sessions.complete(work.id, claim, session, work.deadline, clock.instant());
            return session;
        } catch (RuntimeException failure) {
            sessions.release(work.id, claim);
            throw failure;
        }
    }
}
