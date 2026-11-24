package com.biletflow.biletflow.support.integration.notifications;

import com.biletflow.biletflow.common.events.IntegrationEvent;
import com.biletflow.biletflow.common.events.IntegrationEventHandler;
import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.support.domain.supportcase.MessageAuthorType;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Outbox consumer for Support notifications. A failed delivery is propagated so JdbcEventBus
 * leaves the event pending and retries it. Delivery is at-least-once, so SMTP duplicates remain possible.
 */
@Component
public class SupportNotificationEventHandler implements IntegrationEventHandler {
    private static final Logger log = LoggerFactory.getLogger(SupportNotificationEventHandler.class);
    private final IamOhs iam;
    private final EventManagementOhs eventManagement;
    private final EmailSender emailSender;

    public SupportNotificationEventHandler(IamOhs iam, EventManagementOhs eventManagement, EmailSender emailSender) {
        this.iam = Objects.requireNonNull(iam);
        this.eventManagement = Objects.requireNonNull(eventManagement);
        this.emailSender = Objects.requireNonNull(emailSender);
    }

    @Override
    public String eventType() {
        return SupportNotificationEvent.TYPE;
    }

    @Override
    public void handle(IntegrationEvent event) {
        SupportNotificationEvent notification = SupportNotificationEvent.from(event);
        switch (notification.kind()) {
            case CASE_OPENED -> {
                sendToUser(notification.requesterUserId(), "Support case received",
                    "Your support case " + notification.caseId() + " has been received.", notification.caseId());
                notifySupportStaff(notification, "New support case needs attention",
                    "A new support case " + notification.caseId() + " is waiting for review.");
            }
            case CASE_ESCALATED -> {
                notifyPlatformAdministrators("Support case escalated",
                    "Support case " + notification.caseId() + " was escalated for review.", notification.caseId());
                sendToUser(notification.requesterUserId(), "Support case escalated",
                    "Your support case " + notification.caseId() + " was forwarded to platform support.", notification.caseId());
            }
            case CASE_ASSIGNED -> {
                sendToUser(notification.requesterUserId(), "Support case assigned",
                    "Your support case " + notification.caseId() + " has been assigned to a support agent.", notification.caseId());
                sendToUser(notification.assignedSupportUserId(), "Support case assigned to you",
                    "Support case " + notification.caseId() + " has been assigned to you.", notification.caseId());
            }
            case STATUS_CHANGED -> sendToUser(notification.requesterUserId(), "Support case status updated",
                "The status of your support case " + notification.caseId() + " changed to " + notification.status() + ".",
                notification.caseId());
            case MESSAGE_ADDED -> {
                if (notification.authorType() == MessageAuthorType.SUPPORT_AGENT) {
                    sendToUser(notification.requesterUserId(), "New support case message",
                        "A support agent replied to your case " + notification.caseId() + ".", notification.caseId());
                } else if (notification.assignedSupportUserId() != null) {
                    sendToUser(notification.assignedSupportUserId(), "New support case message",
                        "A requester replied to support case " + notification.caseId() + ".", notification.caseId());
                } else {
                    notifySupportStaff(notification, "Requester replied to an unassigned case",
                        "A requester replied to support case " + notification.caseId() + ", which has no assigned agent.");
                }
            }
        }
    }

    private void notifySupportStaff(SupportNotificationEvent event, String subject, String body) {
        if (event.caseType() == com.biletflow.biletflow.support.domain.supportcase.SupportCaseType.PLATFORM_SUPPORT) {
            notifyPlatformAdministrators(subject, body, event.caseId());
            return;
        }

        if (event.eventId() == null) {
            throw new IllegalStateException("Attendee support notification has no eventId: " + event.caseId());
        }
        var routing = eventManagement.supportRouting(event.eventId());
        if (routing.isEmpty()) {
            // A deleted/missing event must not silently strand an attendee case. Escalate to platform support.
            log.warn("No Event Management routing information for support case {}; escalating to platform administrators",
                event.caseId());
            notifyPlatformAdministrators(subject, body, event.caseId());
            return;
        }
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(routing.get().organizerUserId());
        recipients.addAll(routing.get().supportStaffUserIds());
        for (Long userId : recipients) {
            sendToUser(userId, subject, body, event.caseId());
        }
    }

    private void notifyPlatformAdministrators(String subject, String body, UUID caseId) {
        for (VerifiedUserEmailView admin : iam.findVerifiedAdministratorEmails()) {
            send(admin, subject, body, caseId);
        }
    }

    private void sendToUser(Long userId, String subject, String body, UUID caseId) {
        if (userId == null) return;
        try {
            iam.findVerifiedUserEmail(userId).ifPresent(user -> send(user, subject, body, caseId));
        } catch (RuntimeException exception) {
            log.warn("Could not send notification for support case {}; outbox delivery will retry", caseId, exception);
            throw exception;
        }
    }

    private void send(VerifiedUserEmailView user, String subject, String body, UUID caseId) {
        try {
            emailSender.send(new EmailSender.Email(UUID.randomUUID(), user.email(), subject, body));
        } catch (RuntimeException exception) {
            log.warn("Could not send notification for support case {}; outbox delivery will retry", caseId, exception);
            throw exception;
        }
    }
}
