package com.biletflow.biletflow.ticketinventory.application.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable email work. Enqueue joins issuance; worker operations use short, separate transactions. */
public interface TicketDeliveryStore {
    record Claim(UUID ticketId, UUID token, UUID messageId) {}

    void enqueue(List<UUID> ticketIds, Instant now);
    void request(UUID ticketId, Instant now);
    Optional<Claim> claim(Instant now);
    void complete(Claim claim, boolean skipped, Instant now);
    void retry(Claim claim, Instant now);
}
