package com.biletflow.biletflow.ticketinventory.domain.ticket;

import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository {
    Ticket save(Ticket ticket);

    List<Ticket> saveAll(List<Ticket> tickets);

    Optional<Ticket> findById(TicketId id);

    Optional<Ticket> findByTicketCode(TicketCode ticketCode);

    List<Ticket> findAllByOrderId(OrderId orderId);

    List<Ticket> findAllByEventId(UUID eventId);

    List<Ticket> findAllByOwnerUserId(Long ownerUserId);
}
