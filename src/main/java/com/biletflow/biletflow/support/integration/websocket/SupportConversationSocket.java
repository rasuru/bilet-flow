package com.biletflow.biletflow.support.integration.websocket;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import com.biletflow.biletflow.support.application.SupportCaseApplicationService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class SupportConversationSocket extends TextWebSocketHandler {

    private static final int TICKET_SECONDS = 30;
    private static final int AUTHENTICATION_SECONDS = 10;

    private record Access(UUID caseId, long userId, boolean administrator, Instant expiresAt) {}
    private record Ticket(Access access, Instant redeemBy) {}
    private record Connection(WebSocketSession session, Instant authenticateBy, Access access) {}

    private final ConcurrentHashMap<UUID, Ticket> tickets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    private final SupportCaseApplicationService support;
    private final CurrentActor actor;
    private final Clock clock;

    public SupportConversationSocket(SupportCaseApplicationService support, CurrentActor actor, Clock clock) {
        this.support = support;
        this.actor = actor;
        this.clock = clock;
    }

    public UUID issueTicket(UUID caseId, Instant tokenExpiresAt) {
        long userId = actor.requireUserId();
        Instant now = clock.instant();
        if (tokenExpiresAt == null || !now.isBefore(tokenExpiresAt)) {
            throw new UnauthenticatedActorException("Sign in again to receive live support updates");
        }
        var access = new Access(caseId, userId, actor.isAdministrator(), tokenExpiresAt);
        if (!canRead(access)) throw new AuthorizationException("You are not allowed to access this support case");
        UUID ticket = UUID.randomUUID();
        tickets.put(ticket, new Ticket(access, now.plusSeconds(TICKET_SECONDS)));
        return ticket;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(128);
        var guarded = new ConcurrentWebSocketSessionDecorator(session, 5_000, 4_096);
        connections.put(session.getId(), new Connection(guarded, clock.instant().plusSeconds(AUTHENTICATION_SECONDS), null));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var pending = connections.get(session.getId());
        if (pending == null) return;
        Instant now = clock.instant();
        if (pending.access() != null || !now.isBefore(pending.authenticateBy())) {
            close(pending, CloseStatus.POLICY_VIOLATION);
            return;
        }
        Ticket ticket;
        try {
            ticket = tickets.remove(UUID.fromString(message.getPayload()));
        } catch (IllegalArgumentException ignored) {
            close(pending, CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (ticket == null || !now.isBefore(ticket.redeemBy()) || !canRead(ticket.access())) {
            close(pending, CloseStatus.POLICY_VIOLATION);
            return;
        }
        var established = new Connection(pending.session(), pending.authenticateBy(), ticket.access());
        if (connections.replace(session.getId(), pending, established)) send(established, new TextMessage("ready"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable failure) {
        var connection = connections.get(session.getId());
        if (connection != null) close(connection, CloseStatus.SERVER_ERROR);
    }

    void notifyCase(UUID caseId) {
        for (var connection : connections.values()) {
            var access = connection.access();
            if (access != null && caseId.equals(access.caseId())) {
                if (canRead(access)) send(connection, new TextMessage("changed"));
                else close(connection, CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    @Scheduled(fixedDelay = 15_000)
    public void maintainConnections() {
        Instant now = clock.instant();
        tickets.values().removeIf(ticket -> !now.isBefore(ticket.redeemBy()));
        for (var connection : connections.values()) {
            var access = connection.access();
            if (access == null) {
                if (!now.isBefore(connection.authenticateBy())) close(connection, CloseStatus.POLICY_VIOLATION);
            } else if (now.isBefore(access.expiresAt())) {
                send(connection, new PingMessage());
            } else {
                close(connection, CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    private boolean canRead(Access access) {
        return clock.instant().isBefore(access.expiresAt()) &&
            support.canReadConversation(access.caseId(), access.userId(), access.administrator());
    }

    private void send(Connection connection, WebSocketMessage<?> message) {
        try {
            connection.session().sendMessage(message);
        } catch (IOException | RuntimeException ignored) {
            close(connection, CloseStatus.SERVER_ERROR);
        }
    }

    private void close(Connection connection, CloseStatus status) {
        if (!connections.remove(connection.session().getId(), connection)) return;
        try {
            connection.session().close(status);
        } catch (IOException ignored) {
            // The transport may already be closed.
        }
    }
}
