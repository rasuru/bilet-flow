package com.biletflow.biletflow.support.integration.notifications;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import com.biletflow.biletflow.common.events.IntegrationEventHandler;
import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import com.biletflow.biletflow.support.domain.supportcase.SupportCase;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseId;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseRepository;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Outbox consumer for Support notification facts. Delivery is at-least-once:
 * a process crash after SMTP accepts a message but before outbox acknowledgement
 * can cause a retry and a duplicate email.
 */
@Component
public class SupportNotificationEventHandler implements IntegrationEventHandler {
    private static final Logger log = LoggerFactory.getLogger(SupportNotificationEventHandler.class);
    private final IamOhs iam;
    private final EmailSender emailSender;
    private final SupportCaseRepository cases;

    public SupportNotificationEventHandler(IamOhs iam, EmailSender emailSender, SupportCaseRepository cases) {
        this.iam = Objects.requireNonNull(iam);
        this.emailSender = Objects.requireNonNull(emailSender);
        this.cases = Objects.requireNonNull(cases);
    }

    @Override
    public String eventType() {
        return SupportNotificationEvent.TYPE;
    }

    @Override
    public void handle(IntegrationEvent event) {
        SupportNotificationEvent notification = SupportNotificationEvent.from(event);
        switch (notification.kind()) {
            case CASE_OPENED -> send(notification.requesterUserId(), "Support case received",
                "Your support case " + notification.caseId() + " has been received.", notification.caseId());
            case STATUS_CHANGED -> {
                SupportCase supportCase = find(notification.caseId());
                if (supportCase != null) send(supportCase.getRequesterUserId(), "Support case status updated",
                    "The status of your support case " + notification.caseId() + " changed to " + notification.status() + ".", notification.caseId());
            }
            case MESSAGE_ADDED -> {
                SupportCase supportCase = find(notification.caseId());
                if (supportCase == null) return;
                Long recipient = notification.authorType() == MessageAuthorType.SUPPORT_AGENT
                    ? supportCase.getRequesterUserId() : supportCase.getAssignedSupportUserId();
                if (recipient != null) send(recipient, "New support case message",
                    "A new message was added to support case " + notification.caseId() + ".", notification.caseId());
            }
        }
    }

    private SupportCase find(UUID id) {
        return cases.findById(new SupportCaseId(id)).orElse(null);
    }

    private void send(Long userId, String subject, String body, UUID caseId) {
        try {
            iam.findVerifiedUserEmail(userId).ifPresent(user ->
                emailSender.send(new EmailSender.Email(UUID.randomUUID(), user.email(), subject, body)));
        } catch (RuntimeException exception) {
            // Propagate so JdbcEventBus leaves the outbox row pending and retries it.
            log.warn("Could not send notification for support case {}; outbox delivery will retry", caseId, exception);
            throw exception;
        }
    }
}
