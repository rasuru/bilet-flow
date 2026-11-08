package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.paymentsmonetization.application.view.*;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivationStatus;
import com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount.PayoutAccountRepository;
import com.biletflow.biletflow.ticketinventory.ohs.PaidSalesInventoryOhs;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final ActivationRepository activations;
    private final PaymentLedger ledger;
    private final PaymentService payments;
    private final EventManagementOhs events;
    private final PaidSalesInventoryOhs inventory;
    private final PaymentTermsProvider activationTerms;
    private final Clock clock;
    private final PayoutAccountRepository payoutAccounts;

    public PaymentQueryService(
        ActivationRepository activations,
        PaymentLedger ledger,
        PaymentService payments,
        EventManagementOhs events,
        PaidSalesInventoryOhs inventory,
        PaymentTermsProvider activationTerms,
        Clock clock,
        PayoutAccountRepository payoutAccounts
    ) {
        this.activations = Objects.requireNonNull(activations);
        this.ledger = Objects.requireNonNull(ledger);
        this.payments = Objects.requireNonNull(payments);
        this.events = Objects.requireNonNull(events);
        this.inventory = Objects.requireNonNull(inventory);
        this.activationTerms = Objects.requireNonNull(activationTerms);
        this.clock = Objects.requireNonNull(clock);
        this.payoutAccounts = Objects.requireNonNull(payoutAccounts);
    }

    public ActivationView activation(UUID eventId, long actorId) {
        var activation = activations.find(eventId).orElseThrow(() -> new EntityNotFoundException("Activation not found"));
        payments.authorized(activation.paymentId(), actorId);
        return new ActivationView(
            eventId,
            activation.aggregate().getStatus().name(),
            activation.paymentId(),
            true,
            activation.verificationSimulated()
        );
    }

    public ActivationChecklistView checklist(UUID eventId, long actorId) {
        requireOrganizer(eventId, actorId);
        var scope = events.salesScope(eventId, clock.instant());
        var activation = activations.find(eventId);
        boolean eventEligible = !scope.cancelled() && scope.eventStartsAt().isAfter(clock.instant());
        boolean paidTicketType = inventory.hasPaidTicketType(eventId);
        boolean requested = activation.isPresent();
        boolean verified = activation.map(ActivationRepository.Activation::verificationSimulated).orElse(false);
        long accountOwner = activation.map(a -> a.aggregate().getOrganizerId()).orElse(actorId);
        boolean payoutAccount = payoutAccounts.findByOrganizerId(accountOwner).isPresent();
        var status = activation.map(a -> a.aggregate().getStatus()).orElse(null);
        boolean feePaid = status == EventPaidSalesActivationStatus.ACTIVE || status == EventPaidSalesActivationStatus.SUSPENDED;
        var items = List.of(
            new ActivationChecklistView.Item(
                "EVENT_ACCEPTS_PAID_SALES",
                eventEligible,
                false,
                "The event is not cancelled and has not started."
            ),
            new ActivationChecklistView.Item(
                "PAID_TICKET_TYPE",
                paidTicketType,
                false,
                "The event has at least one ticket type with a price above zero."
            ),
            new ActivationChecklistView.Item(
                "IDENTITY_VERIFICATION",
                verified,
                true,
                "Identity and payout verification. Simulated for the sandbox; confirmed in the activation request."
            ),
            new ActivationChecklistView.Item(
                "PAYOUT_ACCOUNT_REGISTERED",
                payoutAccount,
                true,
                "A payout account is registered. The IBAN is format-checked only; no bank verifies it."
            ),
            new ActivationChecklistView.Item(
                "PAID_EVENT_TERMS_ACCEPTED",
                requested,
                false,
                "The sandbox paid-event terms are accepted in the activation request."
            ),
            new ActivationChecklistView.Item(
                "ACTIVATION_FEE_PAID",
                feePaid,
                false,
                "The one-time activation fee payment has been confirmed by the bank."
            )
        );
        return new ActivationChecklistView(
            eventId,
            status == null ? "NOT_REQUESTED" : status.name(),
            items,
            eventEligible && paidTicketType && payoutAccount && !feePaid,
            status == EventPaidSalesActivationStatus.ACTIVE,
            activationTerms.current().activationFee(),
            true
        );
    }

    public RevenueView revenue(long organizerId) {
        return new RevenueView(true, true, "KZT", ledger.summary(organizerId));
    }

    public RevenueView eventRevenue(UUID eventId, long actorId) {
        requireOrganizer(eventId, actorId);
        return new RevenueView(true, true, "KZT", ledger.eventSummary(eventId));
    }

    private void requireOrganizer(UUID eventId, long actorId) {
        if (!events.canManageTicketing(eventId, actorId)) throw new AuthorizationException("Only the event's organizers can view this");
    }
}
