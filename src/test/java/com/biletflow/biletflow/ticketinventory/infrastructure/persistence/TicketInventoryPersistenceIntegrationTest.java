package com.biletflow.biletflow.ticketinventory.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.SeatHoldReference;
import com.biletflow.biletflow.ticketinventory.application.sale.TicketSaleApplicationService;
import com.biletflow.biletflow.ticketinventory.application.sale.command.CompleteSaleCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.IssueTicketItem;
import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.AssignedSeatingInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketRepository;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketType;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@Import(TicketInventoryPersistenceIntegrationTest.TestClockConfiguration.class)
@TestPropertySource(properties = { "spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.minimum-idle=2" })
class TicketInventoryPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Autowired
    private EventInventoryRepository inventoryRepository;

    @Autowired
    private TicketSaleApplicationService ticketSaleApplicationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private TicketRepository ticketRepository;

    @MockitoBean
    private TicketTypeRepository ticketTypeRepository;

    @Test
    void failedTicketPersistenceRollsBackSoldSeatTransition() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        SeatId seatId = new SeatId(UUID.randomUUID());

        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory inventory = EventInventory.create(
            eventId,
            HoldExpiry.defaultExpiry(),
            AssignedSeatingInventory.create(Map.of(seatId, "VIP"))
        );

        var hold = inventory.holdSeat(ticketTypeId, seatId, new SessionId("session-a"), NOW);

        inTransaction(() -> inventoryRepository.save(inventory));

        TicketType ticketType = mock(TicketType.class);

        when(ticketType.getEventId()).thenReturn(eventId);

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));

        when(ticketRepository.findAllByOrderId(any())).thenReturn(List.of());

        when(ticketRepository.saveAll(anyList())).thenThrow(new RuntimeException("simulated ticket persistence failure"));

        CompleteSaleCommand command = new CompleteSaleCommand(
            orderId,
            eventId,
            List.of(new SeatHoldReference(hold.getId().value())),
            List.of(new IssueTicketItem(ticketTypeId.value(), "buyer@example.com", null, seatId.value()))
        );

        assertThrows(RuntimeException.class, () -> ticketSaleApplicationService.complete(command));

        EventInventory reloaded = inventoryRepository.findByEventId(eventId).orElseThrow();

        var persistedHold = ((AssignedSeatingInventory) reloaded.getMode())
            .getHolds()
            .stream()
            .filter(candidate -> candidate.getId().equals(hold.getId()))
            .findFirst()
            .orElseThrow();

        assertEquals(SeatHoldStatus.HELD, persistedHold.getStatus());

        assertNull(persistedHold.getOrderId());
    }

    @Test
    void concurrentTransactionsCannotBothWinSameSeat() throws Exception {
        UUID eventId = UUID.randomUUID();

        SeatId seatId = new SeatId(UUID.randomUUID());

        TicketTypeId ticketTypeId = TicketTypeId.generate();

        EventInventory initial = EventInventory.create(
            eventId,
            HoldExpiry.defaultExpiry(),
            AssignedSeatingInventory.create(Map.of(seatId, "STANDARD"))
        );

        inTransaction(() -> inventoryRepository.save(initial));

        CountDownLatch bothLoaded = new CountDownLatch(2);

        CountDownLatch allowWrites = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Boolean> first = executor.submit(contender(eventId, ticketTypeId, seatId, "session-a", bothLoaded, allowWrites));

        Future<Boolean> second = executor.submit(contender(eventId, ticketTypeId, seatId, "session-b", bothLoaded, allowWrites));

        try {
            assertTrue(bothLoaded.await(5, TimeUnit.SECONDS), "Both transactions must load the aggregate before either writes");

            allowWrites.countDown();

            boolean firstWon = first.get(10, TimeUnit.SECONDS);

            boolean secondWon = second.get(10, TimeUnit.SECONDS);

            assertNotEquals(firstWon, secondWon, "Exactly one concurrent transaction must win the seat");

            assertTrue(firstWon || secondWon, "At least one transaction must successfully hold the seat");
        } finally {
            allowWrites.countDown();
            executor.shutdownNow();
        }

        EventInventory reloaded = inventoryRepository.findByEventId(eventId).orElseThrow();

        long activeHolds = ((AssignedSeatingInventory) reloaded.getMode())
            .getHolds()
            .stream()
            .filter(hold -> hold.getSeatId().equals(seatId) && hold.getStatus() == SeatHoldStatus.HELD)
            .count();

        assertEquals(1, activeHolds);
    }

    private Callable<Boolean> contender(
        UUID eventId,
        TicketTypeId ticketTypeId,
        SeatId seatId,
        String sessionId,
        CountDownLatch bothLoaded,
        CountDownLatch allowWrites
    ) {
        return () -> {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);

            try {
                transaction.executeWithoutResult(status -> {
                    EventInventory inventory = inventoryRepository.findByEventId(eventId).orElseThrow();

                    bothLoaded.countDown();

                    await(allowWrites);

                    inventory.holdSeat(ticketTypeId, seatId, new SessionId(sessionId), NOW);

                    inventoryRepository.save(inventory);
                });

                return true;
            } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
                return false;
            }
        };
    }

    private void inTransaction(Runnable action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> action.run());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new RuntimeException(ex);
        }
    }

    @Configuration
    static class TestClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
