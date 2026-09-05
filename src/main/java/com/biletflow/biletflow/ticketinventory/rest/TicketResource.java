package com.biletflow.biletflow.ticketinventory.rest;

import com.biletflow.biletflow.ticketinventory.application.ticket.TicketService;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.ClaimTicketCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketResource {

    private final TicketService ticketService;

    public TicketResource(TicketService ticketService) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService cannot be null");
    }

    @GetMapping("/mine")
    public List<TicketView> getMine() {
        return ticketService.getMine();
    }

    @PostMapping("/{ticketId}/claim")
    public ResponseEntity<TicketView> claim(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(ticketService.claim(new ClaimTicketCommand(ticketId)));
    }
}
