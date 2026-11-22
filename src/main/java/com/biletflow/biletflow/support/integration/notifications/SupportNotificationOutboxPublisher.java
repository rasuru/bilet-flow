package com.biletflow.biletflow.support.integration.notifications;

import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.support.application.event.SupportCaseAssignedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseEscalatedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Writes durable notification facts to common_outbox before the support transaction commits. */
@Component
public class SupportNotificationOutboxPublisher {
    private final EventBus outbox;

    public SupportNotificationOutboxPublisher(EventBus outbox) {
        this.outbox = Objects.requireNonNull(outbox);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOpened(SupportCaseOpenedEvent event) {
        publish(SupportNotificationEvent.opened(event), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAssigned(SupportCaseAssignedEvent event) {
        publish(SupportNotificationEvent.assigned(event), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStatusChanged(SupportCaseStatusChangedEvent event) {
        publish(SupportNotificationEvent.statusChanged(event), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onMessageAdded(SupportMessageAddedEvent event) {
        publish(SupportNotificationEvent.messageAdded(event), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onEscalated(SupportCaseEscalatedEvent event) {
        publish(SupportNotificationEvent.escalated(event), event.occurredAt());
    }

    private void publish(SupportNotificationEvent event, java.time.Instant occurredAt) {
        outbox.publish(event.toIntegrationEvent(occurredAt));
    }
}
