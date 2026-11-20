package com.biletflow.biletflow.support.integration.notifications;

import com.biletflow.biletflow.common.events.EventBus;
import com.biletflow.biletflow.support.application.event.SupportCaseOpenedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Writes notification facts to common_outbox before the support transaction commits. */
@Component
public class SupportNotificationOutboxPublisher {
    private final EventBus outbox;

    public SupportNotificationOutboxPublisher(EventBus outbox) {
        this.outbox = Objects.requireNonNull(outbox);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOpened(SupportCaseOpenedEvent event) {
        outbox.publish(SupportNotificationEvent.opened(event).toIntegrationEvent(event.occurredAt().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStatusChanged(SupportCaseStatusChangedEvent event) {
        outbox.publish(SupportNotificationEvent.statusChanged(event).toIntegrationEvent(event.occurredAt().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onMessageAdded(SupportMessageAddedEvent event) {
        outbox.publish(SupportNotificationEvent.messageAdded(event).toIntegrationEvent(event.occurredAt().toString()));
    }
}
