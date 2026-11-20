package com.biletflow.biletflow.support.application;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
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
import java.time.Clock;
import java.time.Instant;
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

    public SupportCaseApplicationService(
        SupportCaseRepository supportCases,
        CurrentActor currentActor,
        ApplicationEventPublisher events,
        Clock clock
    ) {
        this.supportCases = Objects.requireNonNull(supportCases);
        this.currentActor = Objects.requireNonNull(currentActor);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public SupportCaseView open(OpenSupportCaseCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Long requesterId = currentActor.requireUserId();
        Instant now = Instant.now(clock);

        SupportCase supportCase = SupportCase.open(
            command.type(),
            requesterId,
            command.subject(),
            command.category(),
            command.initialMessage(),
            command.eventId(),
            command.orderId(),
            command.ticketId(),
            now
        );

        SupportCase saved = supportCases.save(supportCase);
        events.publishEvent(new SupportCaseOpenedEvent(saved.getId().value(), requesterId, now));
        return SupportCaseView.from(saved);
    }

    @Transactional
    public SupportCaseView reply(ReplyToSupportCaseCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Long actorId = currentActor.requireUserId();
        SupportCase supportCase = requireCase(command.caseId());
        MessageAuthorType authorType;

        if (supportCase.isOwnedBy(actorId)) {
            supportCase.replyAsRequester(actorId, command.content(), Instant.now(clock));
            authorType = MessageAuthorType.REQUESTER;
        } else if (currentActor.isAdministrator()) {
            supportCase.replyAsSupportAgent(actorId, command.content(), Instant.now(clock));
            authorType = MessageAuthorType.SUPPORT_AGENT;
        } else {
            throw new AuthorizationException("You are not allowed to reply to this support case");
        }

        SupportCase saved = supportCases.save(supportCase);
        events.publishEvent(new SupportMessageAddedEvent(
            saved.getId().value(), actorId, authorType, saved.getUpdatedAt()
        ));
        return SupportCaseView.from(saved);
    }

    @Transactional
    public SupportCaseView changeStatus(ChangeSupportCaseStatusCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Long actorId = requireAdministrator();
        SupportCase supportCase = requireCase(command.caseId());
        CaseStatus previousStatus = supportCase.getStatus();
        Instant now = Instant.now(clock);

        supportCase.changeStatus(command.status(), now);
        SupportCase saved = supportCases.save(supportCase);
        events.publishEvent(new SupportCaseStatusChangedEvent(
            saved.getId().value(), previousStatus, saved.getStatus(), actorId, now
        ));
        return SupportCaseView.from(saved);
    }

    @Transactional
    public SupportCaseView assign(AssignSupportCaseCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Long actorId = requireAdministrator();
        SupportCase supportCase = requireCase(command.caseId());
        Instant now = Instant.now(clock);

        supportCase.assignTo(command.supportUserId(), now);
        SupportCase saved = supportCases.save(supportCase);
        events.publishEvent(new SupportCaseAssignedEvent(
            saved.getId().value(), saved.getAssignedSupportUserId(), actorId, saved.getAssignedAt()
        ));
        return SupportCaseView.from(saved);
    }

    public SupportCaseView get(UUID caseId) {
        Long actorId = currentActor.requireUserId();
        SupportCase supportCase = requireCase(caseId);
        authorizeRead(supportCase, actorId);
        return SupportCaseView.from(supportCase);
    }

    public List<SupportCaseView> list() {
        Long actorId = currentActor.requireUserId();
        List<SupportCase> cases = currentActor.isAdministrator()
            ? supportCases.findAll()
            : supportCases.findByRequesterUserId(actorId);

        return cases.stream().map(SupportCaseView::from).toList();
    }

    private SupportCase requireCase(UUID caseId) {
        Objects.requireNonNull(caseId, "caseId cannot be null");
        return supportCases.findById(new SupportCaseId(caseId))
            .orElseThrow(() -> new EntityNotFoundException("Support case not found: " + caseId));
    }

    private void authorizeRead(SupportCase supportCase, Long actorId) {
        if (!currentActor.isAdministrator() && !supportCase.isOwnedBy(actorId)) {
            throw new AuthorizationException("You are not allowed to access this support case");
        }
    }

    private Long requireAdministrator() {
        return currentActor.requireAdministrator();
    }
}
