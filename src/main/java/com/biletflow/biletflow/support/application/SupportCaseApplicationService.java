package com.biletflow.biletflow.support.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.ordercheckout.ohs.OrderCheckoutOhs;
import com.biletflow.biletflow.ordercheckout.ohs.dto.SupportOrderReference;
import com.biletflow.biletflow.support.application.command.AssignSupportCaseCommand;
import com.biletflow.biletflow.support.application.command.ChangeSupportCaseStatusCommand;
import com.biletflow.biletflow.support.application.command.OpenSupportCaseCommand;
import com.biletflow.biletflow.support.application.command.ReplyToSupportCaseCommand;
import com.biletflow.biletflow.support.application.event.SupportCaseAssignedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import com.biletflow.biletflow.support.application.view.SupportCaseView;
import com.biletflow.biletflow.support.domain.supportcase.CaseStatus;
import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import com.biletflow.biletflow.support.domain.supportcase.SupportCase;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseId;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseRepository;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseType;
import com.biletflow.biletflow.ticketinventory.ohs.TicketInventoryOhs;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketSupportReferenceView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SupportCaseApplicationService {

    private final SupportCaseRepository supportCases;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final EventManagementOhs eventManagement;
    private final OrderCheckoutOhs orderCheckout;
    private final TicketInventoryOhs ticketInventory;
    private final IamOhs iam;

    public SupportCaseApplicationService(
        SupportCaseRepository supportCases,
        CurrentActor currentActor,
        ApplicationEventPublisher events,
        Clock clock,
        EventManagementOhs eventManagement,
        OrderCheckoutOhs orderCheckout,
        TicketInventoryOhs ticketInventory,
        IamOhs iam
    ) {
        this.supportCases = Objects.requireNonNull(supportCases);
        this.currentActor = Objects.requireNonNull(currentActor);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
        this.eventManagement = Objects.requireNonNull(eventManagement);
        this.orderCheckout = Objects.requireNonNull(orderCheckout);
        this.ticketInventory = Objects.requireNonNull(ticketInventory);
        this.iam = Objects.requireNonNull(iam);
    }

    @Transactional
    public SupportCaseView open(OpenSupportCaseCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Long requesterId = currentActor.requireUserId();
        SupportContext context = resolveContext(command, requesterId);
        Instant now = Instant.now(clock);

        SupportCase supportCase = SupportCase.open(
            command.type(), requesterId, command.subject(), command.category(), command.initialMessage(),
            context.eventId(), context.orderId(), context.ticketId(), now
        );

        SupportCase saved = supportCases.save(supportCase);
        events.publishEvent(new SupportCaseOpenedEvent(
            saved.getId().value(), requesterId, saved.getType(), saved.getEventId(), now
        ));
        return SupportCaseView.from(saved);
    }

    @Transactional
    public SupportCaseView reply(ReplyToSupportCaseCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Long actorId = currentActor.requireUserId();
        SupportCase supportCase = requireCase(command.caseId());
        MessageAuthorType authorType;
        Instant now = Instant.now(clock);

        if (supportCase.isOwnedBy(actorId)) {
            supportCase.replyAsRequester(actorId, command.content(), now);
            authorType = MessageAuthorType.REQUESTER;
        } else if (canHandle(supportCase, actorId)) {
            supportCase.replyAsSupportAgent(actorId, command.content(), now);
            authorType = MessageAuthorType.SUPPORT_AGENT;
        } else {
            throw new AuthorizationException("You are not allowed to reply to this support case");
        }

        SupportCase saved = supportCases.save(supportCase);
        events.publishEvent(new SupportMessageAddedEvent(
            saved.getId().value(), actorId, authorType, saved.getType(), saved.getEventId(),
            saved.getRequesterUserId(), saved.getAssignedSupportUserId(), saved.getUpdatedAt()
        ));
        return SupportCaseView.from(saved);
    }

    @Transactional
    public SupportCaseView changeStatus(ChangeSupportCaseStatusCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Long actorId = currentActor.requireUserId();
        SupportCase supportCase = requireCase(command.caseId());
        requireCanHandle(supportCase, actorId);
        CaseStatus previousStatus = supportCase.getStatus();
        Instant now = Instant.now(clock);

        supportCase.changeStatus(command.status(), now);
        SupportCase saved = supportCases.save(supportCase);
        events.publishEvent(new SupportCaseStatusChangedEvent(
            saved.getId().value(), previousStatus, saved.getStatus(), actorId, saved.getType(),
            saved.getEventId(), saved.getRequesterUserId(), now
        ));
        return SupportCaseView.from(saved);
    }

    @Transactional
    public SupportCaseView assign(AssignSupportCaseCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Long actorId = currentActor.requireUserId();
        SupportCase supportCase = requireCase(command.caseId());
        requireCanHandle(supportCase, actorId);
        validateAssignmentTarget(supportCase, command.supportUserId());
        if (Objects.equals(supportCase.getAssignedSupportUserId(), command.supportUserId())) {
            return SupportCaseView.from(supportCase);
        }

        Instant now = Instant.now(clock);
        supportCase.assignTo(command.supportUserId(), now);
        SupportCase saved = supportCases.save(supportCase);
        events.publishEvent(new SupportCaseAssignedEvent(
            saved.getId().value(), saved.getAssignedSupportUserId(), actorId, saved.getType(),
            saved.getEventId(), saved.getRequesterUserId(), saved.getAssignedAt()
        ));
        return SupportCaseView.from(saved);
    }

    public SupportCaseView get(UUID caseId) {
        Long actorId = currentActor.requireUserId();
        SupportCase supportCase = requireCase(caseId);
        authorizeRead(supportCase, actorId);
        return SupportCaseView.from(supportCase);
    }

    /** Uses the same access policy as the REST case read. */
    public boolean canReadConversation(UUID caseId, long userId, boolean administrator) {
        return supportCases.findById(new SupportCaseId(caseId))
            .map(supportCase -> supportCase.isOwnedBy(userId) || canHandle(supportCase, userId, administrator))
            .orElse(false);
    }

    public List<SupportCaseView> list() {
        Long actorId = currentActor.requireUserId();
        if (currentActor.isAdministrator()) {
            return supportCases.findAll().stream().map(SupportCaseView::from).toList();
        }

        LinkedHashMap<UUID, SupportCase> visible = new LinkedHashMap<>();
        for (SupportCase supportCase : supportCases.findByRequesterUserId(actorId)) {
            visible.put(supportCase.getId().value(), supportCase);
        }

        List<UUID> managedEventIds = eventManagement.findSupportManagedEventIds(actorId);
        if (!managedEventIds.isEmpty()) {
            for (SupportCase supportCase : supportCases.findByEventIdIn(managedEventIds)) {
                if (supportCase.getType() == SupportCaseType.ATTENDEE_SUPPORT) {
                    visible.put(supportCase.getId().value(), supportCase);
                }
            }
        }
        return visible.values().stream().map(SupportCaseView::from).toList();
    }

    private SupportContext resolveContext(OpenSupportCaseCommand command, Long requesterId) {
        if (command.type() != SupportCaseType.ATTENDEE_SUPPORT) {
            if (command.eventId() != null || command.orderId() != null || command.ticketId() != null) {
                throw new IllegalArgumentException("Platform support cases cannot include attendee event/order/ticket references");
            }
            return new SupportContext(null, null, null);
        }

        UUID eventId = command.eventId();
        UUID orderId = command.orderId();
        UUID ticketId = command.ticketId();

        if (ticketId != null) {
            TicketSupportReferenceView ticket = ticketInventory.findSupportReference(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketId));
            if (orderId != null && !orderId.equals(ticket.orderId())) {
                throw new AuthorizationException("The supplied order does not belong to the supplied ticket");
            }
            if (eventId != null && !eventId.equals(ticket.eventId())) {
                throw new AuthorizationException("The supplied event does not match the supplied ticket");
            }

            if (ticket.ownerUserId() == null) {
                SupportOrderReference order = requireOwnedOrder(ticket.orderId(), requesterId);
                if (!order.eventId().equals(ticket.eventId())) {
                    throw new IllegalStateException("Ticket and order event references do not match");
                }
            } else if (!ticket.ownerUserId().equals(requesterId)) {
                throw new AuthorizationException("The supplied ticket does not belong to the current user");
            }
            eventId = ticket.eventId();
            orderId = ticket.orderId();
        } else if (orderId != null) {
            SupportOrderReference order = requireOwnedOrder(orderId, requesterId);
            if (eventId != null && !eventId.equals(order.eventId())) {
                throw new AuthorizationException("The supplied event does not match the supplied order");
            }
            eventId = order.eventId();
        }

        if (eventId == null) {
            throw new IllegalArgumentException("Attendee support cases must include an event, order, or ticket reference");
        }
        UUID validatedEventId = eventId;
        var routing = eventManagement.supportRouting(validatedEventId)
            .orElseThrow(() -> new EntityNotFoundException("Event not found: " + validatedEventId));
        if (orderId == null && ticketId == null && routing.privateEvent() &&
            !eventManagement.canAccessPrivateEvent(validatedEventId, requesterId)) {
            throw new EntityNotFoundException("Event not found: " + validatedEventId);
        }
        return new SupportContext(eventId, orderId, ticketId);
    }

    private SupportOrderReference requireOwnedOrder(UUID orderId, Long requesterId) {
        SupportOrderReference order = orderCheckout.findSupportReference(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        if (!order.ownerUserId().equals(requesterId)) {
            throw new AuthorizationException("The supplied order does not belong to the current user");
        }
        return order;
    }

    private void validateAssignmentTarget(SupportCase supportCase, Long supportUserId) {
        if (supportUserId == null || supportUserId <= 0) {
            throw new IllegalArgumentException("supportUserId must be positive");
        }
        boolean authorizedTarget = supportCase.getType() == SupportCaseType.ATTENDEE_SUPPORT
            ? eventManagement.canHandleAttendeeSupport(supportCase.getEventId(), supportUserId)
            : iam.isPlatformAdministrator(supportUserId);
        if (!authorizedTarget) {
            throw new AuthorizationException("The selected user is not authorized to handle this support case");
        }
    }

    private SupportCase requireCase(UUID caseId) {
        Objects.requireNonNull(caseId, "caseId cannot be null");
        return supportCases.findById(new SupportCaseId(caseId))
            .orElseThrow(() -> new EntityNotFoundException("Support case not found: " + caseId));
    }

    private boolean canHandle(SupportCase supportCase, Long actorId) {
        return canHandle(supportCase, actorId, currentActor.isAdministrator());
    }

    private boolean canHandle(SupportCase supportCase, Long actorId, boolean administrator) {
        return administrator || (supportCase.getType() == SupportCaseType.ATTENDEE_SUPPORT
            && supportCase.getEventId() != null
            && eventManagement.canHandleAttendeeSupport(supportCase.getEventId(), actorId));
    }

    private void requireCanHandle(SupportCase supportCase, Long actorId) {
        if (!canHandle(supportCase, actorId)) {
            throw new AuthorizationException("You are not authorized to handle this support case");
        }
    }

    private void authorizeRead(SupportCase supportCase, Long actorId) {
        if (!supportCase.isOwnedBy(actorId) && !canHandle(supportCase, actorId)) {
            throw new AuthorizationException("You are not allowed to access this support case");
        }
    }

    private record SupportContext(UUID eventId, UUID orderId, UUID ticketId) {}
}
