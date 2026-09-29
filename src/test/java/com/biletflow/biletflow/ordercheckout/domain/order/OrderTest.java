package com.biletflow.biletflow.ordercheckout.domain.order;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.checkout.AssignedSeatingCheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSession;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionId;
import com.biletflow.biletflow.ordercheckout.domain.checkout.GeneralAdmissionCheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private CheckoutSession createCompletedGeneralAdmissionCheckout() {
        CheckoutSession session = new CheckoutSession(
            CheckoutSessionId.generate(),
            1L,
            UUID.randomUUID(),
            InventoryMode.GENERAL_ADMISSION
        );

        session.addItem(
            new GeneralAdmissionCheckoutItem(
                UUID.randomUUID(),
                Money.zeroKzt()
            )
        );
        session.complete();

        return session;
    }

    private CheckoutSession createCompletedReservedSeatingCheckout() {
        CheckoutSession session = new CheckoutSession(
            CheckoutSessionId.generate(),
            1L,
            UUID.randomUUID(),
            InventoryMode.RESERVED_SEATING
        );

        session.addItem(
            new AssignedSeatingCheckoutItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Money.zeroKzt()
            )
        );
        session.complete();

        return session;
    }

    @Test
    void createFromCheckout_completedCheckout_createsCompletedOrder() {
        CheckoutSession checkout = createCompletedGeneralAdmissionCheckout();

        Order order = Order.createFromCheckout(checkout);

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(checkout.getOwnerId(), order.getOwnerId());
        assertEquals(checkout.getEventId(), order.getEventId());
        assertEquals(checkout.getInventoryMode(), order.getInventoryMode());
        assertEquals(checkout.getTotalPrice(), order.getTotalPrice());
        assertEquals(checkout.getFinalPrice(), order.getFinalPrice());
        assertEquals(checkout.getDiscountAmount(), order.getDiscountAmount());
        assertEquals(checkout.getPaymentId(), order.getPaymentId());
    }

    @Test
    void createFromCheckout_inProgressCheckout_throws() {
        CheckoutSession checkout = new CheckoutSession(
            CheckoutSessionId.generate(),
            1L,
            UUID.randomUUID(),
            InventoryMode.GENERAL_ADMISSION
        );

        assertThrows(
            IllegalStateException.class,
            () -> Order.createFromCheckout(checkout)
        );
    }

    @Test
    void createFromCheckout_nullCheckout_throws() {
        assertThrows(
            NullPointerException.class,
            () -> Order.createFromCheckout(null)
        );
    }

    @Test
    void createFromCheckout_generalAdmissionItem_isConvertedToOrderItem() {
        CheckoutSession checkout = createCompletedGeneralAdmissionCheckout();

        Order order = Order.createFromCheckout(checkout);

        assertEquals(1, order.getItems().size());
        assertInstanceOf(
            GeneralAdmissionOrderItem.class,
            order.getItems().get(0)
        );

        GeneralAdmissionOrderItem item =
            (GeneralAdmissionOrderItem) order.getItems().get(0);

        GeneralAdmissionCheckoutItem checkoutItem =
            (GeneralAdmissionCheckoutItem) checkout.getItems().get(0);

        assertEquals(checkoutItem.getTicketTypeId(), item.getTicketTypeId());
        assertEquals(checkoutItem.getPrice(), item.getPrice());
    }

    @Test
    void createFromCheckout_assignedSeatingItem_isConvertedToOrderItem() {
        CheckoutSession checkout = createCompletedReservedSeatingCheckout();

        Order order = Order.createFromCheckout(checkout);

        assertEquals(1, order.getItems().size());
        assertInstanceOf(
            AssignedSeatingOrderItem.class,
            order.getItems().get(0)
        );

        AssignedSeatingOrderItem item =
            (AssignedSeatingOrderItem) order.getItems().get(0);

        AssignedSeatingCheckoutItem checkoutItem =
            (AssignedSeatingCheckoutItem) checkout.getItems().get(0);

        assertEquals(checkoutItem.getTicketTypeId(), item.getTicketTypeId());
        assertEquals(checkoutItem.getSeatId(), item.getSeatId());
        assertEquals(checkoutItem.getPrice(), item.getPrice());
    }

    @Test
    void cancelOrderByCustomer_completedOrder_becomesCancelled() {
        Order order = Order.createFromCheckout(
            createCompletedGeneralAdmissionCheckout()
        );

        order.cancelOrderByCustomer();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(
            CancellationReason.CUSTOMER_REQUEST,
            order.getCancellationReason()
        );
    }

    @Test
    void cancelOrderByManager_completedOrder_becomesCancelled() {
        Order order = Order.createFromCheckout(
            createCompletedGeneralAdmissionCheckout()
        );

        order.cancelOrderByManager();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(
            CancellationReason.EVENT_CANCELLED,
            order.getCancellationReason()
        );
    }

    @Test
    void cancelledOrder_cannotBeCancelledAgain() {
        Order order = Order.createFromCheckout(
            createCompletedGeneralAdmissionCheckout()
        );

        order.cancelOrderByCustomer();

        assertThrows(
            IllegalStateException.class,
            order::cancelOrderByManager
        );
    }

    @Test
    void cancelledOrder_canBeMarkedRefunded() {
        Order order = Order.createFromCheckout(
            createCompletedGeneralAdmissionCheckout()
        );
        order.cancelOrderByCustomer();

        UUID refundId = UUID.randomUUID();

        order.markRefunded(refundId);

        assertEquals(refundId, order.getRefundId());
    }

    @Test
    void completedOrder_cannotBeMarkedRefunded() {
        Order order = Order.createFromCheckout(
            createCompletedGeneralAdmissionCheckout()
        );

        assertThrows(
            IllegalStateException.class,
            () -> order.markRefunded(UUID.randomUUID())
        );
    }

    @Test
    void markRefunded_nullId_throws() {
        Order order = Order.createFromCheckout(
            createCompletedGeneralAdmissionCheckout()
        );
        order.cancelOrderByCustomer();

        assertThrows(
            NullPointerException.class,
            () -> order.markRefunded(null)
        );
    }

    @Test
    void orderItems_areImmutableThroughGetter() {
        Order order = Order.createFromCheckout(
            createCompletedGeneralAdmissionCheckout()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> order.getItems().clear()
        );
    }
}
