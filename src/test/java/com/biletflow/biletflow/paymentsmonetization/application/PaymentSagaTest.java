package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.*;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.common.events.*;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.eventmanagement.ohs.dto.*;
import com.biletflow.biletflow.ordercheckout.application.*;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.*;
import com.biletflow.biletflow.paymentsmonetization.domain.activationfeepayment.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.*;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentWork;
import com.biletflow.biletflow.paymentsmonetization.domain.ticketorderpayment.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.*;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Isolated application tests: real services/domain, fake bank and repositories. Database guarantees have separate ITs. */
public class PaymentSagaTest {

    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void fails(Runnable code) {
        boolean failed = false;
        try {
            code.run();
        } catch (RuntimeException expected) {
            failed = true;
        }
        check(failed, "Expected rejection");
    }

    @Test
    void verifiedCaptureCompletesExactlyOneOrder() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        check(f.stock.issued.isEmpty(), "No tickets before capture");
        f.observe(w, "CHARGE");
        check(f.stock.issued.isEmpty(), "Outbox delivery is the asynchronous boundary");
        f.deliver();
        check(f.orders.rows.size() == 1, "One order");
        check(f.stock.issued.size() == 1, "One ticket issuance");
        f.checkout.onOutcome(new PaymentOutcome(w.id, w.orderId, "SUCCEEDED", null));
        f.observe(w, "CHARGE");
        f.deliver();
        check(f.orders.rows.size() == 1 && f.stock.issued.size() == 1, "Duplicate success does not duplicate fulfillment");
        check(f.ledger.charges.size() == 1, "One ledger charge");
    }

    @Test
    void authorizationIsNotPaymentAndCaptureIsNotRepeated() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.observe(w, "AUTH");
        check(f.bank.commands.equals(List.of("charge")), "Capture dispatched once");
        f.observe(w, "AUTH");
        check(f.bank.commands.size() == 1, "Unresolved capture is not retried");
        check(f.stock.issued.isEmpty(), "AUTH cannot issue tickets");
        f.observe(w, "CHARGE");
        f.deliver();
        check(f.orders.rows.size() == 1, "Verified capture fulfills");
    }

    @Test
    void unknownRefundIsReconciledWithoutReposting() {
        var f = new Fixture();
        var v = f.paid();
        var w = f.work(v);
        f.checkout.refund(v.id(), 7, "Customer requested full refund");
        check(f.stock.refundPending, "Admission blocked before bank call");
        f.bank.throwAfterMutation = true;
        f.observe(w, "CHARGE");
        f.observe(w, "CHARGE");
        check(Collections.frequency(f.bank.commands, "refund") == 1, "Timeout must not cause a second refund");
        check("PENDING".equals(f.checkout.get(v.id(), 1).refundState()), "Timeout is not refund failure");
        f.observe(w, "REFUND");
        check(w.reviewRequired, "An ambiguous response cannot prove the full refund amount");
        f.access.resolve(w.id, 99, w.operationId, "FULL_REFUND_CONFIRMED_COMPLETED", "Bank case confirms full amount returned");
        f.deliver();
        check("REFUNDED".equals(f.checkout.get(v.id(), 1).refundState()), "Verified full refund completes saga");
        check(f.ledger.refunds.size() == 1 && !f.stock.refundPending, "Refund booked exactly once");
        f.observe(w, "REFUND");
        f.deliver();
        check(f.stock.refundCompletions == 1, "Repeated refund is idempotent");
    }

    @Test
    void abandonedCheckoutVoidsAuthorization() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.checkout.cancel(v.id(), 1);
        f.observe(w, "AUTH");
        check(f.bank.commands.equals(List.of("cancel")), "Abandoned AUTH is voided, never captured");
        f.observe(w, "CANCEL");
        f.deliver();
        check(f.orders.rows.isEmpty(), "Cancelled checkout has no order");
    }

    @Test
    void capturedPaymentAfterExpiryIsRefunded() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.clock.advance(601);
        f.checkout.expire(v.id());
        f.observe(w, "CHARGE");
        f.deliver();
        check(f.orders.rows.isEmpty(), "No overselling after hold expiry");
        check(w.payment.getRefund().isPresent(), "Late payment gets compensation");
        f.observe(w, "CHARGE");
        check(f.bank.commands.contains("refund"), "Compensation reaches bank");
        f.observe(w, "REFUND");
        f.deliver();
        check("REFUNDED".equals(f.checkout.get(v.id(), 1).refundState()), "Expired checkout records refund");
    }

    @Test
    void delayedOutcomeAfterHoldExpiryCannotIssueTickets() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.observe(w, "CHARGE");
        f.clock.advance(601);
        f.deliver();
        check(f.stock.issued.isEmpty() && w.payment.getRefund().isPresent(), "Delayed consumer compensates expired hold");
    }

    @Test
    void ticketIssuanceFailureLeavesSuccessAvailableForRetry() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.observe(w, "CHARGE");
        f.stock.failIssuance = true;
        var e = f.queue.remove(0);
        fails(() -> f.checkout.onOutcome(PaymentOutcome.from(e)));
        check(f.orders.rows.isEmpty(), "No completed order after issuance failure");
        f.stock.failIssuance = false;
        f.checkout.onOutcome(PaymentOutcome.from(e));
        check(f.orders.rows.size() == 1, "Retry finishes the paid order");
    }

    @Test
    void refundCanArriveBeforeSuccessDelivery() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.observe(w, "CHARGE");
        f.observe(w, "REFUND");
        f.access.resolve(w.id, 99, w.operationId, "FULL_REFUND_CONFIRMED_COMPLETED", "Bank case confirms external full refund");
        Collections.reverse(f.queue);
        f.deliver();
        check(f.stock.issued.isEmpty() && f.orders.rows.isEmpty(), "Reordered outcomes cannot issue refunded tickets");
        check("REFUNDED".equals(f.checkout.get(v.id(), 1).refundState()), "Refund survives reordering");
    }

    @Test
    void providerMismatchesNeverFulfillOrMutateBank() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.bank.next = new SettlementEvidence(
            w.invoiceId,
            UUID.randomUUID().toString(),
            new BigDecimal("0.01"),
            "KZT",
            true,
            SettlementStatus.CAPTURED,
            w.id.toString()
        );
        f.reconciler.reconcile(w.id);
        check(w.reviewRequired && f.queue.isEmpty() && f.bank.commands.isEmpty(), "Amount mismatch requires review");
    }

    @Test
    void callbackIsOnlyAHintAndRequiresItsSecret() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        fails(() -> f.access.callback(w.invoiceId, "wrong"));
        f.access.callback(w.invoiceId, w.callbackSecret);
        check("PENDING".equals(w.status()) && f.queue.isEmpty(), "Callback cannot confirm payment");
    }

    @Test
    void failedPaymentReleasesHoldAndLateCaptureCompensates() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.observe(w, "FAILED");
        f.deliver();
        check("PAYMENT_FAILED".equals(f.checkout.get(v.id(), 1).status()), "Failed payment terminates checkout");
        check(f.stock.released.contains(v.id()), "Hold released");
        f.observe(w, "CHARGE");
        f.deliver();
        check(w.payment.getRefund().isPresent() && f.orders.rows.isEmpty(), "Late contradictory capture is recovered");
    }

    @Test
    void idempotencyAndAuthorizationAreEnforced() {
        var f = new Fixture();
        UUID id = UUID.randomUUID();
        var req = f.request();
        var a = f.checkout.create(id, 1, req);
        var b = f.checkout.create(id, 1, req);
        check(a.paymentId().equals(b.paymentId()) && f.works.rows.size() == 1, "One payment per checkout");
        fails(() -> f.checkout.create(id, 2, req));
        fails(() -> f.checkout.get(id, 2));
        fails(() -> f.checkout.refund(id, 2, "No permission"));
        fails(() ->
            f.checkout.create(
                id,
                1,
                new CheckoutService.Request(
                    f.event,
                    List.of(new CheckoutInventoryOhs.Selection(UUID.randomUUID(), List.of(), List.of("other@example.com")))
                )
            )
        );
    }

    @Test
    void freeCheckoutNeverCallsBank() {
        var f = new Fixture();
        f.stock.price = Money.zeroKzt();
        var v = f.start();
        check("COMPLETED".equals(v.status()) && v.paymentId() == null && f.works.rows.isEmpty(), "Free checkout completes without payment");
        f.checkout.refund(v.id(), 7, "Cancel registration");
        check("CANCELLED".equals(f.checkout.get(v.id(), 1).status()), "Organizer can cancel free order");
    }

    @Test
    void bankOutageIsNotPaymentFailure() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.bank.unavailable = true;
        f.reconciler.reconcile(w.id);
        check("PENDING".equals(w.status()) && f.queue.isEmpty(), "Transport failure leaves payment unresolved");
    }

    @Test
    void activationFeesAreNotOrganizerRevenueAndLateFeesAreRefunded() {
        var f = new Fixture();
        f.activations.rows.clear();
        UUID id = f.service.activate(f.event, 7, true, true);
        var w = f.works.find(id).orElseThrow();
        f.observe(w, "CHARGE");
        check(f.activations.find(f.event).orElseThrow().aggregate().permitsPaidSales(), "Verified activation fee enables paid sales");
        check(f.ledger.charges.isEmpty(), "Activation fee is not ticket revenue");
        var g = new Fixture();
        g.activations.rows.clear();
        UUID late = g.service.activate(g.event, 7, true, true);
        var old = g.works.find(late).orElseThrow();
        g.clock.advance(1801);
        UUID replacement = g.service.activate(g.event, 7, true, true);
        check(!replacement.equals(late), "Expired activation may be restarted");
        g.observe(old, "CHARGE");
        check("REFUND_REQUESTED".equals(old.status()), "Old fee settles to compensation");
        g.observe(old, "CHARGE");
        check(g.bank.commands.contains("refund"), "Late activation fee is refunded");
    }

    @Test
    void pendingRefundPreventsCheckInAndExternalRefundInvalidatesUsedTicket() {
        var t = com.biletflow.biletflow.ticketinventory.domain.ticket.Ticket.issue(
            new com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId(UUID.randomUUID()),
            UUID.randomUUID(),
            new com.biletflow.biletflow.ticketinventory.domain.common.OrderId(UUID.randomUUID()),
            new com.biletflow.biletflow.ticketinventory.domain.ticket.AttendeeEmail("a@example.com"),
            null,
            null,
            Instant.now()
        );
        t.prepareRefund();
        fails(t::checkIn);
        t.restoreAfterFailedRefund();
        t.checkIn();
        t.applyConfirmedRefund();
        check(
            t.getStatus() == com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus.REFUNDED,
            "Financial truth invalidates admission"
        );
        check(!t.applyConfirmedRefund(), "Repeated financial refund does not release inventory twice");
    }

    @Test
    void onlyAuditedOperatorEvidenceAllowsRetryOfAnUnknownMutation() {
        var f = new Fixture();
        var v = f.paid();
        var w = f.work(v);
        f.checkout.refund(v.id(), 7, "Full refund");
        f.observe(w, "CHARGE");
        UUID op = w.operationId;
        fails(() -> f.access.resolve(w.id, 99, UUID.randomUUID(), "CONFIRMED_NOT_EXECUTED_RETRY", "Bank case 1"));
        f.access.resolve(w.id, 99, op, "REFUND_CONFIRMED_REJECTED", "Bank case 1");
        f.deliver();
        check("FAILED".equals(f.checkout.get(v.id(), 1).refundState()) && !f.stock.refundPending, "Confirmed rejection restores admission");
        UUID oldRefund = w.payment.getRefund().orElseThrow().getId().value();
        f.checkout.refund(v.id(), 7, "Retry after bank rejection");
        check(!w.payment.getRefund().orElseThrow().getId().value().equals(oldRefund), "Definitively failed refund gets a fresh attempt");
    }

    @Test
    void capturedFundsCannotRegressToAnAuthorization() {
        var f = new Fixture();
        var v = f.paid();
        var w = f.work(v);
        f.observe(w, "AUTH");
        check(w.reviewRequired && f.bank.commands.isEmpty(), "A regressed bank state must not trigger another capture");
    }

    @Test
    void anotherApplicationsSharedSandboxPaymentCannotFulfillCheckout() {
        var f = new Fixture();
        var v = f.start();
        var w = f.work(v);
        f.bank.next = new SettlementEvidence(
            w.invoiceId,
            UUID.randomUUID().toString(),
            w.amount,
            "KZT",
            true,
            SettlementStatus.CAPTURED,
            UUID.randomUUID().toString()
        );
        f.reconciler.reconcile(w.id);
        check(
            w.reviewRequired && f.queue.isEmpty() && f.stock.issued.isEmpty(),
            "Invoice/amount match is insufficient on a shared terminal"
        );
    }

    @Test
    void acknowledgedFullRefundCompletesEvenWhenBankAmountBecomesZero() {
        var f = new Fixture();
        var v = f.paid();
        var w = f.work(v);
        f.checkout.refund(v.id(), 7, "Full refund");
        f.observe(w, "CHARGE");
        check(w.operationAcknowledged, "Successful bank response is retained");
        f.bank.next = new SettlementEvidence(
            w.invoiceId,
            w.bankTransactionId,
            BigDecimal.ZERO,
            "KZT",
            true,
            SettlementStatus.REFUNDED,
            w.id.toString()
        );
        f.reconciler.reconcile(w.id);
        f.deliver();
        check(
            "REFUNDED".equals(f.checkout.get(v.id(), 1).refundState()) && f.ledger.refunds.size() == 1,
            "Mutable refund amount cannot break acknowledged full refund confirmation"
        );
    }

    @Test
    void externalPartialRefundCannotBeBookedAsAFullRefund() {
        var f = new Fixture();
        var v = f.paid();
        var w = f.work(v);
        f.bank.next = new SettlementEvidence(
            w.invoiceId,
            w.bankTransactionId,
            new BigDecimal("500.00"),
            "KZT",
            true,
            SettlementStatus.REFUNDED,
            w.id.toString()
        );
        f.reconciler.reconcile(w.id);
        check(w.reviewRequired && f.ledger.refunds.isEmpty(), "Partial or ambiguous bank refund requires evidence of full amount");
    }

    @Test
    void sessionRetriesReuseDurableFormWithoutCallingBankTwice() {
        var fixture = new Fixture();
        var work = fixture.work(fixture.start());
        var store = new FormSessions();
        var form = new PaymentGateway.Session(Map.of(), "sandbox", Map.of("auth", Map.of("expires_in", 3600)));
        fixture.bank.form = form;
        var service = new PaymentSessionService(store, fixture.bank, fixture.clock);
        check(service.open(work) == form, "First form returned");
        check(service.open(work) == form, "Durable form reused");
        check(fixture.bank.sessionCalls == 1, "Only one provider session");
    }

    @Test
    void failedSessionPublicationReleasesOnlyItsClaim() {
        var fixture = new Fixture();
        var work = fixture.work(fixture.start());
        var store = new FormSessions();
        store.superseded = true;
        fixture.bank.form = new PaymentGateway.Session(Map.of(), "sandbox", Map.of());
        fails(() -> new PaymentSessionService(store, fixture.bank, fixture.clock).open(work));
        check(store.released.equals(store.claim), "Failure cleans up the matching claim");
    }

    @Test
    void expiredSessionIsRejectedBeforeClaimOrNetworkCall() {
        var fixture = new Fixture();
        var work = fixture.work(fixture.start());
        var store = new FormSessions();
        fixture.clock.now = work.deadline;
        fails(() -> new PaymentSessionService(store, fixture.bank, fixture.clock).open(work));
        check(store.claim == null && fixture.bank.sessionCalls == 0, "No claim or provider call for expired payment");
    }

    static final class FormSessions implements PaymentSessionStore {

        UUID claim, released;
        PaymentGateway.Session cached;
        boolean superseded;

        public PaymentGateway.Session claim(UUID paymentId, UUID claim, Instant now) {
            this.claim = claim;
            return cached;
        }

        public void complete(UUID paymentId, UUID claim, PaymentGateway.Session session, Instant deadline, Instant now) {
            if (superseded) throw new IllegalStateException("Claim superseded");
            cached = session;
        }

        public void release(UUID paymentId, UUID claim) {
            released = claim;
        }
    }

    public static void main(String[] args) throws Exception {
        int tests = 0;
        for (var method : PaymentSagaTest.class.getDeclaredMethods())
            if (method.isAnnotationPresent(Test.class)) {
                try {
                    method.invoke(new PaymentSagaTest());
                    tests++;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw new AssertionError(method.getName(), e.getCause());
                }
            }
        System.out.println(tests + " saga tests passed; " + assertions + " assertions");
    }

    static final class MutableClock extends Clock {

        Instant now = Instant.parse("2026-10-18T12:00:00Z");

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    static final class LocalTransactions implements Transactions {

        public <T> T required(java.util.function.Supplier<T> work) {
            return work.get();
        }
    }

    static final class Secrets implements CallbackSecrets {

        public String generate() {
            return UUID.randomUUID().toString();
        }

        public boolean matches(String expected, String supplied) {
            return expected.equals(supplied);
        }
    }

    static final class Audit implements FinancialAudit {

        Audit(Clock c) {}

        public void record(String c, UUID id, Long actor, String action) {}

        public void recordDetails(String c, UUID id, Long actor, String action, String details) {}
    }

    static final class Locks implements TransactionLocks {

        Locks() {}

        public void lock(String key) {}
    }

    static final class Bank implements PaymentGateway {

        SettlementEvidence next;
        Session form;
        int sessionCalls;
        boolean throwAfterMutation;
        boolean unavailable;
        List<String> commands = new ArrayList<>();

        public boolean acceptsInvoiceId(String id) {
            return id != null && id.matches("[0-9]{6,15}");
        }

        public void validateAmount(BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.TEN) < 0 || amount.scale() > 2) throw new IllegalArgumentException(
                "Unsupported amount"
            );
        }

        public Session session(PaymentWork w) {
            sessionCalls++;
            if (form == null) throw new IllegalStateException("No provider form");
            return form;
        }

        public SettlementEvidence inspect(String invoice) {
            if (unavailable) throw new IllegalStateException("offline");
            return next;
        }

        public void mutate(String t, String op, String id) {
            commands.add(op);
            if (throwAfterMutation) throw new IllegalStateException("response lost");
        }
    }

    static final class Works implements PaymentWorkRepository {

        Map<UUID, PaymentWork> rows = new HashMap<>();
        int sequence = 100000;

        public String nextInvoice() {
            return Integer.toString(sequence++);
        }

        public Optional<PaymentWork> find(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        public Optional<PaymentWork> findOrder(UUID id) {
            return rows
                .values()
                .stream()
                .filter(w -> id.equals(w.orderId))
                .findFirst();
        }

        public Optional<PaymentWork> findInvoice(String id) {
            return rows
                .values()
                .stream()
                .filter(w -> id.equals(w.invoiceId))
                .findFirst();
        }

        public void insert(PaymentWork w) {
            rows.put(w.id, w);
        }

        public void save(PaymentWork w) {
            rows.put(w.id, w);
        }

        public List<UUID> requiringReview() {
            return List.of();
        }

        public List<UUID> due(Instant now) {
            return new ArrayList<>(rows.keySet());
        }

        public boolean lease(UUID id, Instant now, Instant until) {
            return true;
        }

        public void schedule(UUID id, Instant when) {}

        public void wake(UUID id, Instant when) {}
    }

    static final class Activations implements ActivationRepository {

        Map<UUID, Activation> rows = new HashMap<>();

        public Optional<Activation> find(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        public void save(Activation a) {
            rows.put(a.aggregate().getEventId(), a);
        }
    }

    static final class Ledger implements PaymentLedger {

        Set<UUID> charges = new HashSet<>(),
            refunds = new HashSet<>();

        public void charge(PaymentWork w) {
            charges.add(w.id);
        }

        public void refund(PaymentWork w, UUID r) {
            refunds.add(w.id);
        }

        public Map<String, BigDecimal> summary(long o) {
            return Map.of();
        }
    }

    static final class Events implements EventManagementOhs {

        boolean open = true;

        public EventTicketingConfigurationView getTicketingConfiguration(UUID id) {
            throw new UnsupportedOperationException();
        }

        public EventSeatMapView getSeatMap(UUID id) {
            throw new UnsupportedOperationException();
        }

        public boolean canManageTicketing(UUID e, Long u) {
            return u == 7;
        }

        public boolean canCheckIn(UUID e, Long u) {
            return false;
        }

        public SalesScope salesScope(UUID e, Instant now) {
            return new SalesScope(e, 7, open, !open, now.plusSeconds(86400));
        }
    }

    static final class Sessions implements CheckoutSessionRepository {

        Map<UUID, CheckoutSession> rows = new HashMap<>();

        public CheckoutSession save(CheckoutSession c) {
            rows.put(c.getId().value(), c);
            return c;
        }

        public Optional<CheckoutSession> findById(CheckoutSessionId id) {
            return Optional.ofNullable(rows.get(id.value()));
        }

        public List<CheckoutSession> findAllByEventId(UUID id) {
            return rows
                .values()
                .stream()
                .filter(c -> c.getEventId().equals(id))
                .toList();
        }

        public List<CheckoutSession> findAllByOwnerUserId(Long id) {
            return rows
                .values()
                .stream()
                .filter(c -> c.getOwnerId() == id)
                .toList();
        }
    }

    static final class Orders implements OrderRepository {

        Map<UUID, Order> rows = new HashMap<>();

        public Order save(Order o) {
            rows.put(o.getId(), o);
            return o;
        }

        public Optional<Order> findById(OrderId id) {
            return Optional.ofNullable(rows.get(id.value()));
        }

        public List<Order> findAllByEventId(UUID id) {
            return rows
                .values()
                .stream()
                .filter(o -> o.getEventId().equals(id))
                .toList();
        }

        public List<Order> findAllByOwnerUserId(Long id) {
            return rows
                .values()
                .stream()
                .filter(o -> o.getOwnerId() == id)
                .toList();
        }
    }

    static final class Processes implements CheckoutProcessRepository {

        Map<UUID, CheckoutProcess> rows = new HashMap<>();

        public Optional<CheckoutProcess> find(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        public void save(CheckoutProcess p) {
            rows.put(p.id(), p);
        }

        public List<UUID> active() {
            return new ArrayList<>(rows.keySet());
        }
    }

    static final class Stock implements CheckoutInventoryOhs {

        final Clock clock;
        Money price = Money.kzt(1000);
        boolean failIssuance, refundPending;
        int refundCompletions;
        Set<UUID> released = new HashSet<>();
        Map<UUID, List<IssuedTicket>> issued = new HashMap<>();

        Stock(Clock c) {
            clock = c;
        }

        public Reservation reserve(UUID event, UUID checkout, List<Selection> selections) {
            List<Line> lines = new ArrayList<>();
            for (var s : selections)
                for (int i = 0; i < s.attendeeEmails().size(); i++) lines.add(
                    new Line(s.ticketTypeId(), s.seats().isEmpty() ? null : s.seats().get(i), price, s.attendeeEmails().get(i))
                );
            return new Reservation(lines, List.of(new Hold(UUID.randomUUID(), false)), clock.instant().plusSeconds(600), false);
        }

        public void release(UUID e, UUID id, List<Hold> h) {
            released.add(id);
        }

        public List<IssuedTicket> issue(UUID e, UUID id, long owner, List<Line> l, List<Hold> h) {
            if (failIssuance) throw new IllegalStateException("temporary storage error");
            return issued.computeIfAbsent(id, x ->
                List.of(new IssuedTicket(UUID.randomUUID(), UUID.randomUUID(), "VALID", l.get(0).ticketTypeId(), null, l.get(0).email()))
            );
        }

        public List<IssuedTicket> tickets(UUID id) {
            return issued.getOrDefault(id, List.of());
        }

        public void prepareRefund(UUID e, UUID id) {
            refundPending = true;
        }

        public void completeRefund(UUID e, UUID id) {
            refundPending = false;
            refundCompletions++;
        }

        public void failRefund(UUID e, UUID id) {
            refundPending = false;
        }

        public void cancelFreeOrder(UUID e, UUID id) {}
    }

    static final class Fixture {

        UUID event = UUID.randomUUID(),
            type = UUID.randomUUID();
        MutableClock clock = new MutableClock();
        Works works = new Works();
        Activations activations = new Activations();
        Events events = new Events();
        Locks locks = new Locks();
        Audit audit = new Audit(clock);
        Ledger ledger = new Ledger();
        Bank bank = new Bank();
        List<IntegrationEvent> queue = new ArrayList<>();
        PaymentTerms terms = new PaymentTerms(new BigDecimal("1000"), new BigDecimal("0.03"));
        Secrets secrets = new Secrets();
        Sessions sessions = new Sessions();
        Orders orders = new Orders();
        Processes processes = new Processes();
        Stock stock = new Stock(clock);
        PaymentService service = new PaymentService(works, activations, events, locks, audit, clock, terms, bank, secrets);
        PaymentTransitions transitions = new PaymentTransitions(works, activations, ledger, queue::add, audit, locks, events, clock);
        PaymentAccessService access = new PaymentAccessService(works, bank, secrets, transitions, audit, clock);
        PaymentReconciler reconciler = new PaymentReconciler(works, bank, transitions, locks, clock, new LocalTransactions());
        CheckoutService checkout = new CheckoutService(
            sessions,
            orders,
            processes,
            stock,
            service,
            events,
            locks,
            audit,
            Object::toString,
            clock
        );

        Fixture() {
            activations.save(
                new ActivationRepository.Activation(
                    new EventPaidSalesActivation(
                        event,
                        7L,
                        clock.instant(),
                        EventPaidSalesActivationStatus.ACTIVE,
                        new ActivationFeePaymentId(UUID.randomUUID()),
                        null,
                        null
                    ),
                    UUID.randomUUID(),
                    true,
                    "test"
                )
            );
        }

        CheckoutService.Request request() {
            return new CheckoutService.Request(
                event,
                List.of(new CheckoutInventoryOhs.Selection(type, List.of(), List.of("a@example.com")))
            );
        }

        CheckoutService.View start() {
            return checkout.create(UUID.randomUUID(), 1, request());
        }

        CheckoutService.View paid() {
            var v = start();
            observe(work(v), "CHARGE");
            deliver();
            return checkout.get(v.id(), 1);
        }

        PaymentWork work(CheckoutService.View v) {
            return works.find(v.paymentId()).orElseThrow();
        }

        void observe(PaymentWork w, String status) {
            String ref = w.isTicket() ? w.payment.getProviderReference() : w.activationPayment.getProviderReference();
            if (ref == null) ref = "00000000-0000-0000-0000-000000000001";
            bank.next = new SettlementEvidence(w.invoiceId, ref, w.amount, "KZT", true, normalize(status), w.id.toString());
            reconciler.reconcile(w.id);
        }

        SettlementStatus normalize(String status) {
            return switch (status) {
                case "AUTH" -> SettlementStatus.AUTHORIZED;
                case "CHARGE" -> SettlementStatus.CAPTURED;
                case "REFUND" -> SettlementStatus.REFUNDED;
                case "CANCEL" -> SettlementStatus.VOIDED;
                case "FAILED" -> SettlementStatus.DECLINED;
                default -> SettlementStatus.UNKNOWN;
            };
        }

        void deliver() {
            while (!queue.isEmpty()) checkout.onOutcome(PaymentOutcome.from(queue.remove(0)));
        }
    }
}
