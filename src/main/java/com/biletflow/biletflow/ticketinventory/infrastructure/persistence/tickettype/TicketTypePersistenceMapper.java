package com.biletflow.biletflow.ticketinventory.infrastructure.persistence.tickettype;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.SalesWindow;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.tickettype.entity.TicketTypeJpaEntity;
import java.util.Currency;
import org.springframework.stereotype.Component;

@Component
public class TicketTypePersistenceMapper {

    public TicketType toDomain(TicketTypeJpaEntity entity) {
        return new TicketType(
            new TicketTypeId(entity.getId()),
            entity.getEventId(),
            entity.getName(),
            entity.getDescription(),
            new Money(entity.getPriceAmount(), Currency.getInstance(entity.getPriceCurrency())),
            entity.getMaxPerOrder(),
            new SalesWindow(entity.getSalesStartAt(), entity.getSalesEndAt()),
            entity.getStatus(),
            entity.getPriceCategory()
        );
    }

    public TicketTypeJpaEntity toNewEntity(TicketType domain) {
        return new TicketTypeJpaEntity(
            domain.getId().value(),
            domain.getEventId(),
            domain.getName(),
            domain.getDescription(),
            domain.getPrice().amount(),
            domain.getPrice().currency().getCurrencyCode(),
            domain.getMaxPerOrder(),
            domain.getSalesWindow().startAt(),
            domain.getSalesWindow().endAt(),
            domain.getStatus(),
            domain.getPriceCategory()
        );
    }

    public void updateEntity(TicketType domain, TicketTypeJpaEntity entity) {
        if (!entity.getId().equals(domain.getId().value())) {
            throw new IllegalArgumentException("Cannot map onto another TicketType id");
        }

        if (!entity.getEventId().equals(domain.getEventId())) {
            throw new IllegalArgumentException("TicketType eventId cannot change");
        }

        if (!java.util.Objects.equals(entity.getPriceCategory(), domain.getPriceCategory())) {
            throw new IllegalArgumentException("TicketType price category cannot change");
        }

        entity.update(
            domain.getName(),
            domain.getDescription(),
            domain.getPrice().amount(),
            domain.getPrice().currency().getCurrencyCode(),
            domain.getMaxPerOrder(),
            domain.getSalesWindow().startAt(),
            domain.getSalesWindow().endAt(),
            domain.getStatus()
        );
    }
}
