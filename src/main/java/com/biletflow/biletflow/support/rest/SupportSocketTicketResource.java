package com.biletflow.biletflow.support.rest;

import com.biletflow.biletflow.support.integration.websocket.SupportConversationSocket;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupportSocketTicketResource {

    private final SupportConversationSocket socket;

    public SupportSocketTicketResource(SupportConversationSocket socket) {
        this.socket = socket;
    }

    public record Ticket(UUID ticket) {}

    @PostMapping("/api/support/cases/{caseId}/socket-ticket")
    public ResponseEntity<Ticket> ticket(@PathVariable UUID caseId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Ticket(socket.issueTicket(caseId, jwt == null ? null : jwt.getExpiresAt())));
    }
}
