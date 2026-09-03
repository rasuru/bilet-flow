package com.biletflow.biletflow.ticketinventory.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.ticketinventory.domain.common.HoldExpiry;
import com.biletflow.biletflow.ticketinventory.domain.common.OrderId;
import com.biletflow.biletflow.ticketinventory.domain.common.SeatId;
import com.biletflow.biletflow.ticketinventory.domain.common.SessionId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.AssignedSeatingInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventory;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.EventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldId;
import com.biletflow.biletflow.ticketinventory.domain.eventinventory.assigned.SeatHoldStatus;
import com.biletflow.biletflow.ticketinventory.domain.tickettype.TicketTypeId;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.EventInventoryPersistenceMapper;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.JpaEventInventoryRepository;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.SpringDataEventInventoryJpaRepository;
import com.biletflow.biletflow.ticketinventory.infrastructure.persistence.eventinventory.entity.EventInventoryJpaEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(
    properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.generate-ddl=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
    }
)
@ContextConfiguration(classes = TicketInventoryPersistenceIntegrationTest.JpaTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicketInventoryPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Autowired
    private EventInventoryRepository inventoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

        CyclicBarrier loadedBarrier = new CyclicBarrier(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Boolean> first = executor.submit(contender(eventId, ticketTypeId, seatId, "session-a", loadedBarrier));

        Future<Boolean> second = executor.submit(contender(eventId, ticketTypeId, seatId, "session-b", loadedBarrier));

        try {
            boolean firstWon = first.get(10, TimeUnit.SECONDS);

            boolean secondWon = second.get(10, TimeUnit.SECONDS);

            assertNotEquals(firstWon, secondWon, "Exactly one concurrent transaction must win the seat");
        } finally {
            executor.shutdownNow();
        }

        EventInventory reloaded = inTransactionWithResult(() -> inventoryRepository.findByEventId(eventId).orElseThrow());

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
        CyclicBarrier loadedBarrier
    ) {
        return () -> {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);

            try {
                transaction.executeWithoutResult(status -> {
                    EventInventory inventory = inventoryRepository.findByEventId(eventId).orElseThrow();

                    await(loadedBarrier);

                    inventory.holdSeat(ticketTypeId, seatId, new SessionId(sessionId), NOW);

                    inventoryRepository.save(inventory);
                });

                return true;
            } catch (RuntimeException ex) {
                if (isOptimisticLockFailure(ex)) {
                    return false;
                }

                throw ex;
            }
        };
    }

    private boolean isOptimisticLockFailure(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof OptimisticLockingFailureException || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void inTransaction(Runnable action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> action.run());
    }

    private <T> T inTransactionWithResult(Callable<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        return transaction.execute(status -> {
            try {
                return action.call();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new RuntimeException(ex);
        } catch (BrokenBarrierException | TimeoutException ex) {
            throw new RuntimeException(ex);
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = EventInventoryJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = SpringDataEventInventoryJpaRepository.class)
    @Import({ JpaEventInventoryRepository.class, EventInventoryPersistenceMapper.class })
    static class JpaTestConfiguration {

        @Bean
        RollbackProbe rollbackProbe(EventInventoryRepository inventoryRepository) {
            return new RollbackProbe(inventoryRepository);
        }
    }

    static class RollbackProbe {

        private final EventInventoryRepository inventoryRepository;

        RollbackProbe(EventInventoryRepository inventoryRepository) {
            this.inventoryRepository = inventoryRepository;
        }

        @Transactional
        public void confirmThenFail(UUID eventId, SeatHoldId holdId, OrderId orderId) {
            EventInventory inventory = inventoryRepository.findByEventId(eventId).orElseThrow();

            inventory.confirmSeatHold(holdId, orderId, NOW.plusSeconds(30));

            inventoryRepository.save(inventory);

            throw new RuntimeException("simulated failure after inventory confirmation");
        }
    }
}
