package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.ticket;

import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.ticket.entity.TicketJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class TicketPersistenceMapper {

    public Ticket toDomain(TicketJpaEntity entity) {
        return new Ticket(
            new TicketId(entity.getId()),
            new TicketTypeId(entity.getTicketTypeId()),
            entity.getEventId(),
            new OrderId(entity.getOrderId()),
            new AttendeeEmail(entity.getAttendeeEmail()),
            entity.getOwnerUserId(),
            entity.getSeatId() == null ? null : new SeatId(entity.getSeatId()),
            new TicketCode(entity.getTicketCode()),
            entity.getStatus(),
            entity.getIssuedAt()
        );
    }

    public TicketJpaEntity toNewEntity(Ticket domain) {
        return new TicketJpaEntity(
            domain.getId().value(),
            domain.getTicketTypeId().value(),
            domain.getEventId(),
            domain.getOrderId().value(),
            domain.getAttendeeEmail().value(),
            domain.getOwnerUserId(),
            domain.getSeatId() == null ? null : domain.getSeatId().value(),
            domain.getTicketCode().value(),
            domain.getStatus(),
            domain.getIssuedAt()
        );
    }

    public void updateEntity(Ticket domain, TicketJpaEntity entity) {
        if (!entity.getId().equals(domain.getId().value())) {
            throw new IllegalArgumentException("Cannot map onto another Ticket id");
        }

        entity.updateMutableState(domain.getOwnerUserId(), domain.getStatus());
    }
}
