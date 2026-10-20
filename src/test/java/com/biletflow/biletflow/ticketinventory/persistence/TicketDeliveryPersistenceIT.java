package com.biletflow.biletflow.ticketinventory.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDeliveryCooldownException;
import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDeliveryStore;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.ticket.*;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class TicketDeliveryPersistenceIT {

    @Autowired
    TicketRepository tickets;

    @Autowired
    TicketDeliveryStore deliveries;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    JdbcTemplate jdbc;

    private final List<UUID> created = new ArrayList<>();
    private final Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    private Ticket ticket() {
        var ticket = Ticket.issue(
            TicketTypeId.generate(),
            UUID.randomUUID(),
            new OrderId(UUID.randomUUID()),
            new AttendeeEmail("delivery@example.com"),
            null,
            null,
            now
        );
        created.add(ticket.getId().value());
        return ticket;
    }

    private Ticket queued() {
        var ticket = ticket();
        tx().executeWithoutResult(status -> {
            tickets.save(ticket);
            deliveries.enqueue(List.of(ticket.getId().value()), now);
        });
        return ticket;
    }

    @AfterEach
    void cleanup() {
        tx().executeWithoutResult(status -> {
            for (UUID id : created) {
                jdbc.update("delete from ticket_inventory_delivery where ticket_id=?", id);
                jdbc.update("delete from ticket_inventory_ticket where id=?", id);
            }
        });
    }

    @Test
    void ticketAndEmailQueueRollbackTogether() {
        var ticket = ticket();
        assertThrows(IllegalStateException.class, () ->
            tx().executeWithoutResult(status -> {
                tickets.save(ticket);
                deliveries.enqueue(List.of(ticket.getId().value()), now);
                throw new IllegalStateException("issuance rolled back");
            })
        );
        assertTrue(tickets.findById(ticket.getId()).isEmpty());
        assertEquals(
            0,
            jdbc.queryForObject("select count(*) from ticket_inventory_delivery where ticket_id=?", Integer.class, ticket.getId().value())
        );
    }

    @Test
    void leaseRecoveryRetainsMessageAndRejectsAnOldWorkersAcknowledgement() {
        var ticket = queued();
        var first = deliveries.claim(now).orElseThrow();
        assertEquals(ticket.getId().value(), first.ticketId());
        assertTrue(deliveries.claim(now.plusSeconds(119)).isEmpty());
        var replacement = deliveries.claim(now.plusSeconds(121)).orElseThrow();
        assertEquals(first.messageId(), replacement.messageId());
        assertNotEquals(first.token(), replacement.token());
        deliveries.complete(first, false, now.plusSeconds(122));
        assertEquals(
            "PENDING",
            jdbc.queryForObject("select state from ticket_inventory_delivery where ticket_id=?", String.class, first.ticketId())
        );
        deliveries.complete(replacement, false, now.plusSeconds(123));
        assertTrue(deliveries.claim(now.plusSeconds(1000)).isEmpty());
    }

    @Test
    void failureBackoffAndExplicitResendAreDurableAndRateLimited() {
        var ticket = queued();
        var first = deliveries.claim(now).orElseThrow();
        deliveries.retry(first, now);
        deliveries.request(ticket.getId().value(), now.plusSeconds(1));
        assertTrue(deliveries.claim(now.plusSeconds(29)).isEmpty());
        var retry = deliveries.claim(now.plusSeconds(30)).orElseThrow();
        assertEquals(first.messageId(), retry.messageId());
        deliveries.complete(retry, false, now.plusSeconds(31));
        assertThrows(TicketDeliveryCooldownException.class, () -> deliveries.request(ticket.getId().value(), now.plusSeconds(59)));
        deliveries.request(ticket.getId().value(), now.plusSeconds(60));
        var resend = deliveries.claim(now.plusSeconds(60)).orElseThrow();
        assertNotEquals(first.messageId(), resend.messageId());
    }

    @Test
    void twoWorkersCannotClaimTheSameDeliveryAtOnce() throws Exception {
        queued();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Boolean> claim = () -> {
                start.await();
                return deliveries.claim(now).isPresent();
            };
            var first = pool.submit(claim);
            var second = pool.submit(claim);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
