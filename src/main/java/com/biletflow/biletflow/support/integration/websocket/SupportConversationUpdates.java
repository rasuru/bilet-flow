package com.biletflow.biletflow.support.integration.websocket;

import com.biletflow.biletflow.support.application.event.SupportCaseAssignedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseEscalatedEvent;
import com.biletflow.biletflow.support.application.event.SupportCaseStatusChangedEvent;
import com.biletflow.biletflow.support.application.event.SupportMessageAddedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SupportConversationUpdates {

    private final SupportConversationSocket socket;

    public SupportConversationUpdates(SupportConversationSocket socket) {
        this.socket = socket;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReply(SupportMessageAddedEvent event) {
        socket.notifyCase(event.caseId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatus(SupportCaseStatusChangedEvent event) {
        socket.notifyCase(event.caseId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssignment(SupportCaseAssignedEvent event) {
        socket.notifyCase(event.caseId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEscalation(SupportCaseEscalatedEvent event) {
        socket.notifyCase(event.caseId());
    }
}
