package com.biletflow.biletflow.ordercheckout.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.application.CheckoutProcess;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.CheckoutProcessPersistenceMapper;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.CheckoutSessionPersistenceMapper;
import com.biletflow.biletflow.ordercheckout.persistence.order.OrderPersistenceMapper;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class CheckoutPersistenceMapperTest {

    @Test
    void pricesPromotionSeatsAndOrderCancellationSurviveMapping() {
        var mapper = new CheckoutSessionPersistenceMapper();
        UUID type = UUID.randomUUID(),
            first = UUID.randomUUID(),
            second = UUID.randomUUID(),
            payment = UUID.randomUUID();
        var session = new CheckoutSession(CheckoutSessionId.generate(), 1, UUID.randomUUID(), InventoryMode.RESERVED_SEATING);
        session.addItem(new AssignedSeatingCheckoutItem(type, first, Money.kzt(new BigDecimal("1000"))));
        session.addItem(new AssignedSeatingCheckoutItem(type, second, Money.kzt(new BigDecimal("2000"))));
        session.applyPromotion(UUID.randomUUID(), Money.kzt(new BigDecimal("500")));
        session.awaitPayment();
        var row = mapper.toNewEntity(session);
        var restored = mapper.toDomain(row);
        assertEquals(session.getPromotionId(), restored.getPromotionId());
        assertEquals(session.getFinalPrice(), restored.getFinalPrice());
        assertEquals(first, ((AssignedSeatingCheckoutItem) restored.getItems().get(0)).getSeatId());
        assertEquals(second, ((AssignedSeatingCheckoutItem) restored.getItems().get(1)).getSeatId());
        restored.confirmPayment(payment);
        var order = Order.createFromCheckout(restored);
        order.cancelOrderByManager();
        var orderMapper = new OrderPersistenceMapper();
        var sale = orderMapper.toNewEntity(order);
        var loaded = orderMapper.toDomain(sale);
        assertEquals(order.getStatus(), loaded.getStatus());
        assertEquals(order.getCancellationReason(), loaded.getCancellationReason());
        assertEquals(payment, loaded.getPaymentId());
        assertEquals(restored.getFinalPrice(), loaded.getFinalPrice());
    }

    @Test
    void attendeeAndHoldOrderIsPreservedWithoutSharingMutableCollections() {
        var mapper = new CheckoutProcessPersistenceMapper();
        UUID seat1 = UUID.randomUUID(),
            seat2 = UUID.randomUUID(),
            type = UUID.randomUUID();
        var process = new CheckoutProcess(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            "fingerprint",
            Instant.now().plusSeconds(600),
            List.of(
                new Line(type, seat1, Money.kzt(BigDecimal.TEN), "first@example.com"),
                new Line(type, seat2, Money.kzt(BigDecimal.TEN), "second@example.com")
            ),
            List.of(new Hold(seat1, true), new Hold(seat2, true)),
            UUID.randomUUID(),
            CheckoutRefundStatus.PENDING,
            UUID.randomUUID()
        );
        var entity = mapper.toNewEntity(process);
        var loaded = mapper.toDomain(entity);
        assertEquals(process, loaded);
        assertThrows(UnsupportedOperationException.class, () -> entity.getLines().clear());
        assertThrows(UnsupportedOperationException.class, () -> entity.getHolds().clear());
        assertEquals(2, loaded.lines().size());
        assertEquals(2, loaded.holds().size());
    }
}
