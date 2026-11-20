package com.biletflow.biletflow.support.integration.notifications;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import com.biletflow.biletflow.support.domain.supportcase.SupportCase;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseId;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseRepository;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Best-effort email notifications after commit; notification failure cannot roll back a saved case. */
@Component
public class SupportNotificationEventHandler {
    private static final Logger log = LoggerFactory.getLogger(SupportNotificationEventHandler.class);
    private final IamOhs iam;
    private final EmailSender emailSender;
    private final SupportCaseRepository cases;

    public SupportNotificationEventHandler(IamOhs iam, EmailSender emailSender, SupportCaseRepository cases) {
        this.iam = Objects.requireNonNull(iam);
        this.emailSender = Objects.requireNonNull(emailSender);
        this.cases = Objects.requireNonNull(cases);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOpened(SupportCaseOpenedEvent event) {
        send(event.requesterUserId(), "Support case received", "Your support case " + event.caseId() + " has been received.", event.caseId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(SupportCaseStatusChangedEvent event) {
        SupportCase supportCase = find(event.caseId());
        if (supportCase != null) send(supportCase.getRequesterUserId(), "Support case status updated",
            "The status of your support case " + event.caseId() + " changed to " + event.newStatus() + ".", event.caseId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessage(SupportMessageAddedEvent event) {
        SupportCase supportCase = find(event.caseId());
        if (supportCase == null) return;
        Long recipient = event.authorType() == MessageAuthorType.SUPPORT_AGENT
            ? supportCase.getRequesterUserId() : supportCase.getAssignedSupportUserId();
        if (recipient != null) send(recipient, "New support case message",
            "A new message was added to support case " + event.caseId() + ".", event.caseId());
    }

    private SupportCase find(UUID id) {
        return cases.findById(new SupportCaseId(id)).orElse(null);
    }

    private void send(Long userId, String subject, String body, UUID caseId) {
        try {
            iam.findVerifiedUserEmail(userId).ifPresent(user ->
                emailSender.send(new EmailSender.Email(UUID.randomUUID(), user.email(), subject, body)));
        } catch (RuntimeException exception) {
            log.warn("Could not send notification for support case {}", caseId, exception);
        }
    }
}
