package com.biletflow.biletflow.ordercheckout.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.application.*;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CheckoutPersistenceIT {

    @Autowired
    CheckoutSessionRepository checkouts;

    @Autowired
    OrderRepository orders;

    @Autowired
    CheckoutProcessRepository processes;

    @Autowired
    EntityManager entities;

    @Test
    void relationalItemsAndSagaLinesSurviveFlushAndReloadInOrder() {
        UUID type = UUID.randomUUID(),
            first = UUID.randomUUID(),
            second = UUID.randomUUID(),
            event = UUID.randomUUID();
        var checkout = new CheckoutSession(CheckoutSessionId.generate(), 1, event, InventoryMode.RESERVED_SEATING);
        checkout.addItem(new AssignedSeatingCheckoutItem(type, first, Money.kzt(BigDecimal.TEN)));
        checkout.addItem(new AssignedSeatingCheckoutItem(type, second, Money.kzt(BigDecimal.TEN)));
        checkout.awaitPayment();
        checkouts.save(checkout);
        var process = new CheckoutProcess(
            checkout.getId().value(),
            1,
            event,
            "fingerprint",
            Instant.now().plusSeconds(600).truncatedTo(ChronoUnit.MICROS),
            List.of(
                new Line(type, first, Money.kzt(BigDecimal.TEN), "first@example.com"),
                new Line(type, second, Money.kzt(BigDecimal.TEN), "second@example.com")
            ),
            List.of(new Hold(first, true), new Hold(second, true)),
            UUID.randomUUID(),
            null,
            null
        );
        processes.save(process);
        entities.flush();
        entities.clear();
        var loaded = checkouts.findById(checkout.getId()).orElseThrow();
        assertEquals(first, ((AssignedSeatingCheckoutItem) loaded.getItems().get(0)).getSeatId());
        assertEquals(second, ((AssignedSeatingCheckoutItem) loaded.getItems().get(1)).getSeatId());
        assertEquals(process, processes.find(process.id()).orElseThrow());
        loaded.confirmPayment(process.paymentId());
        checkouts.save(loaded);
        var order = Order.createFromCheckout(loaded);
        orders.save(order);
        orders.save(order);
        entities.flush();
        entities.clear();
        assertEquals(order.getFinalPrice(), orders.findById(new OrderId(order.getId())).orElseThrow().getFinalPrice());
        assertEquals(
            1L,
            entities
                .createQuery("select count(o) from OrderJpaEntity o where o.id = :id", Long.class)
                .setParameter("id", order.getId())
                .getSingleResult()
        );
    }
}
