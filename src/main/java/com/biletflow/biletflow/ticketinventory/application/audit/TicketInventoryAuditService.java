package com.biletflow.biletflow.ticketinventory.application.audit;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ticketinventory.application.audit.TicketInventoryAuditRepository.TicketInventoryAuditRecord;
import com.biletflow.biletflow.ticketinventory.application.audit.TicketInventoryAuditRepository.TicketInventoryAuditType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketInventoryAuditService {

    private final TicketInventoryAuditRepository repository;
    private final Clock clock;

    public TicketInventoryAuditService(TicketInventoryAuditRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID eventId, Long actorUserId, TicketInventoryAuditType type, UUID entityId, String description) {
        repository.append(
            new TicketInventoryAuditRecord(
                UUID.randomUUID(),
                Objects.requireNonNull(eventId, "eventId cannot be null"),
                Instant.now(clock),
                Objects.requireNonNull(actorUserId, "actorUserId cannot be null"),
                Objects.requireNonNull(type, "type cannot be null"),
                Objects.requireNonNull(entityId, "entityId cannot be null"),
                Objects.requireNonNull(description, "description cannot be null")
            )
        );
    }

    public static String format(Money money) {
        return money.amount().toPlainString() + " " + money.currency().getCurrencyCode();
    }
}
