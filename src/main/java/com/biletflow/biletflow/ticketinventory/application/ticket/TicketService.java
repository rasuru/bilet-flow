package com.biletflow.biletflow.ticketinventory.application.ticket;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.*;
import com.biletflow.biletflow.ticketinventory.application.ticket.port.VerifiedUserEmailPort;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketValidationResult;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final EventInventoryRepository inventoryRepository;
    private final VerifiedUserEmailPort verifiedUserEmailPort;
    private final CurrentActor currentActor;

    public TicketService(
        TicketRepository ticketRepository,
        EventInventoryRepository inventoryRepository,
        VerifiedUserEmailPort verifiedUserEmailPort,
        CurrentActor currentActor
    ) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
        this.inventoryRepository = Objects.requireNonNull(inventoryRepository);
        this.verifiedUserEmailPort = Objects.requireNonNull(verifiedUserEmailPort);
        this.currentActor = Objects.requireNonNull(currentActor);
    }

    @Transactional
    public void cancel(CancelTicketCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Ticket ticket = requireTicket(command.ticketId());

        boolean changed = ticket.cancel();
        if (!changed) {
            return;
        }

        EventInventory inventory = requireInventory(ticket.getEventId());

        if (ticket.getSeatId() == null) {
            inventory.reverseGASale(ticket.getTicketTypeId(), 1);
        } else {
            inventory.cancelSoldSeat(ticket.getSeatId(), ticket.getOrderId());
        }

        ticketRepository.save(ticket);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void refund(RefundTicketCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Ticket ticket = requireTicket(command.ticketId());

        boolean changed = ticket.markRefunded();
        if (!changed) {
            return;
        }

        EventInventory inventory = requireInventory(ticket.getEventId());

        if (ticket.getSeatId() == null) {
            inventory.reverseGASale(ticket.getTicketTypeId(), 1);
        } else {
            inventory.refundSoldSeat(ticket.getSeatId(), ticket.getOrderId());
        }

        ticketRepository.save(ticket);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public TicketView checkIn(CheckInTicketCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Ticket ticket = requireTicketByCode(command.ticketCode());

        ensureExpectedEvent(ticket, command.expectedEventId());

        ticket.checkIn();

        return toView(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketView reverseCheckIn(ReverseCheckInCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Ticket ticket = requireTicketByCode(command.ticketCode());

        ensureExpectedEvent(ticket, command.expectedEventId());

        ticket.reverseCheckIn();

        return toView(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketView claim(ClaimTicketCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Long userId = currentActor.requireUserId();
        Ticket ticket = requireTicket(command.ticketId());

        String verifiedEmail = verifiedUserEmailPort
            .findVerifiedEmail(userId)
            .orElseThrow(() -> new AuthorizationException("Authenticated user does not have a verified email"));

        AttendeeEmail normalizedVerifiedEmail = new AttendeeEmail(verifiedEmail);

        if (!ticket.getAttendeeEmail().equals(normalizedVerifiedEmail)) {
            throw new AuthorizationException("Ticket attendee email does not match the authenticated user");
        }

        ticket.linkOwner(userId);

        return toView(ticketRepository.save(ticket));
    }

    public List<TicketView> getMine() {
        Long userId = currentActor.requireUserId();

        return ticketRepository.findAllByOwnerUserId(userId).stream().map(this::toView).toList();
    }

    public TicketView get(UUID ticketId) {
        return toView(requireTicket(ticketId));
    }

    public List<TicketView> getByOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");

        return ticketRepository.findAllByOrderId(new OrderId(orderId)).stream().map(this::toView).toList();
    }

    public List<TicketView> getByOwnerUserId(Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");

        return ticketRepository.findAllByOwnerUserId(userId).stream().map(this::toView).toList();
    }

    public TicketValidationResult validate(UUID ticketCode, UUID expectedEventId) {
        Ticket ticket = requireTicketByCode(ticketCode);

        ensureExpectedEvent(ticket, expectedEventId);

        return new TicketValidationResult(
            ticket.getId().value(),
            ticket.getEventId(),
            ticket.getTicketTypeId().value(),
            ticket.getSeatId() == null ? null : ticket.getSeatId().value(),
            ticket.getStatus(),
            ticket.getStatus() == TicketStatus.VALID
        );
    }

    private Ticket requireTicket(UUID ticketId) {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");

        return ticketRepository
            .findById(new TicketId(ticketId))
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found: " + ticketId));
    }

    private Ticket requireTicketByCode(UUID rawTicketCode) {
        Objects.requireNonNull(rawTicketCode, "ticketCode cannot be null");

        TicketCode ticketCode = new TicketCode(rawTicketCode);

        return ticketRepository
            .findByTicketCode(ticketCode)
            .orElseThrow(() -> new EntityNotFoundException("Ticket not found for supplied ticket code"));
    }

    private EventInventory requireInventory(UUID eventId) {
        return inventoryRepository
            .findByEventId(eventId)
            .orElseThrow(() -> new EntityNotFoundException("EventInventory not found for event: " + eventId));
    }

    private void ensureExpectedEvent(Ticket ticket, UUID expectedEventId) {
        if (!ticket.getEventId().equals(expectedEventId)) {
            throw new IllegalStateException("Ticket is not valid for the selected event");
        }
    }

    private TicketView toView(Ticket ticket) {
        return new TicketView(
            ticket.getId().value(),
            ticket.getTicketTypeId().value(),
            ticket.getEventId(),
            ticket.getOrderId().value(),
            ticket.getAttendeeEmail().value(),
            ticket.getOwnerUserId(),
            ticket.getSeatId() == null ? null : ticket.getSeatId().value(),
            ticket.getTicketCode().value(),
            ticket.getStatus(),
            ticket.getIssuedAt()
        );
    }
}
