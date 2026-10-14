package com.biletflow.biletflow.ordercheckout.domain.order;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.checkout.AssignedSeatingCheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSession;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionId;
import com.biletflow.biletflow.ordercheckout.domain.checkout.GeneralAdmissionCheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    private CheckoutSession createCompletedGeneralAdmissionCheckout() {
        CheckoutSession session = new CheckoutSession(CheckoutSessionId.generate(), 1L, UUID.randomUUID(), InventoryMode.GENERAL_ADMISSION);

        session.addItem(new GeneralAdmissionCheckoutItem(UUID.randomUUID(), Money.zeroKzt()));

        session.completeFreeCheckout();
        return session;
    }

    private CheckoutSession createCompletedReservedSeatingCheckout() {
        CheckoutSession session = new CheckoutSession(CheckoutSessionId.generate(), 1L, UUID.randomUUID(), InventoryMode.RESERVED_SEATING);

        session.addItem(new AssignedSeatingCheckoutItem(UUID.randomUUID(), UUID.randomUUID(), Money.zeroKzt()));

        session.completeFreeCheckout();
        return session;
    }

    private Order createCompletedOrder() {
        return Order.createFromCheckout(createCompletedGeneralAdmissionCheckout());
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
        assertNotNull(order.getId());
    }

    @Test
    void createFromCheckout_inProgressCheckout_throws() {
        CheckoutSession checkout = new CheckoutSession(
            CheckoutSessionId.generate(),
            1L,
            UUID.randomUUID(),
            InventoryMode.GENERAL_ADMISSION
        );

        assertThrows(IllegalStateException.class, () -> Order.createFromCheckout(checkout));
    }

    @Test
    void createFromCheckout_nullCheckout_throws() {
        assertThrows(NullPointerException.class, () -> Order.createFromCheckout(null));
    }

    @Test
    void createFromCheckout_generalAdmissionItem_isConvertedCorrectly() {
        CheckoutSession checkout = createCompletedGeneralAdmissionCheckout();

        Order order = Order.createFromCheckout(checkout);

        assertEquals(1, order.getItems().size());
        assertInstanceOf(GeneralAdmissionOrderItem.class, order.getItems().get(0));

        GeneralAdmissionOrderItem orderItem = (GeneralAdmissionOrderItem) order.getItems().get(0);

        GeneralAdmissionCheckoutItem checkoutItem = (GeneralAdmissionCheckoutItem) checkout.getItems().get(0);

        assertEquals(checkoutItem.getTicketTypeId(), orderItem.getTicketTypeId());
        assertEquals(checkoutItem.getPrice(), orderItem.getPrice());
    }

    @Test
    void createFromCheckout_assignedSeatingItem_isConvertedCorrectly() {
        CheckoutSession checkout = createCompletedReservedSeatingCheckout();

        Order order = Order.createFromCheckout(checkout);

        assertEquals(1, order.getItems().size());
        assertInstanceOf(AssignedSeatingOrderItem.class, order.getItems().get(0));

        AssignedSeatingOrderItem orderItem = (AssignedSeatingOrderItem) order.getItems().get(0);

        AssignedSeatingCheckoutItem checkoutItem = (AssignedSeatingCheckoutItem) checkout.getItems().get(0);

        assertEquals(checkoutItem.getTicketTypeId(), orderItem.getTicketTypeId());
        assertEquals(checkoutItem.getSeatId(), orderItem.getSeatId());
        assertEquals(checkoutItem.getPrice(), orderItem.getPrice());
    }

    @Test
    void cancelOrderByCustomer_completedOrder_becomesCancelled() {
        Order order = createCompletedOrder();

        order.cancelOrderByCustomer();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(CancellationReason.CUSTOMER_REQUEST, order.getCancellationReason());
    }

    @Test
    void cancelOrderByManager_completedOrder_becomesCancelled() {
        Order order = createCompletedOrder();

        order.cancelOrderByManager();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(CancellationReason.ORGANIZER_REQUEST, order.getCancellationReason());
    }

    @Test
    void cancelledOrder_cannotBeCancelledAgain() {
        Order order = createCompletedOrder();
        order.cancelOrderByCustomer();

        assertThrows(IllegalStateException.class, order::cancelOrderByManager);
    }

    @Test
    void markRefunded_cancelledOrder_storesRefundId() {
        Order order = createCompletedOrder();
        order.cancelOrderByCustomer();

        UUID refundId = UUID.randomUUID();
        order.markRefunded(refundId);

        assertEquals(refundId, order.getRefundId());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void markRefunded_sameRefundId_isIdempotent() {
        Order order = createCompletedOrder();
        order.cancelOrderByCustomer();

        UUID refundId = UUID.randomUUID();
        order.markRefunded(refundId);

        assertDoesNotThrow(() -> order.markRefunded(refundId));
        assertEquals(refundId, order.getRefundId());
    }

    @Test
    void markRefunded_differentRefundId_throws() {
        Order order = createCompletedOrder();
        order.cancelOrderByCustomer();

        UUID firstRefundId = UUID.randomUUID();
        UUID secondRefundId = UUID.randomUUID();

        order.markRefunded(firstRefundId);

        assertThrows(IllegalStateException.class, () -> order.markRefunded(secondRefundId));

        assertEquals(firstRefundId, order.getRefundId());
    }

    @Test
    void markRefunded_nonCancelledOrder_throws() {
        Order order = createCompletedOrder();

        assertThrows(IllegalStateException.class, () -> order.markRefunded(UUID.randomUUID()));
    }

    @Test
    void markRefunded_nullRefundId_throws() {
        Order order = createCompletedOrder();
        order.cancelOrderByCustomer();

        assertThrows(NullPointerException.class, () -> order.markRefunded(null));
    }

    @Test
    void getItems_cannotBeModifiedThroughGetter() {
        Order order = createCompletedOrder();

        assertThrows(UnsupportedOperationException.class, () -> order.getItems().clear());
    }

    @Test
    void eventCancellation_hasItsOwnReason() {
        Order order = createCompletedOrder();
        order.cancelOrderForEventCancellation();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(CancellationReason.EVENT_CANCELLED, order.getCancellationReason());
        assertThrows(IllegalStateException.class, order::cancelOrderForEventCancellation);
    }

    @Test
    void repeatedCompletionOfSameCheckout_usesSameOrderIdentity() {
        CheckoutSession checkout = createCompletedGeneralAdmissionCheckout();
        Order first = Order.createFromCheckout(checkout);
        Order retry = Order.createFromCheckout(checkout);
        assertEquals(first.getId(), retry.getId());
        assertEquals(checkout.getId().value(), first.getId());
    }

    @Test
    void differentCheckouts_haveDifferentOrderIdentities() {
        Order first = createCompletedOrder();
        Order second = createCompletedOrder();
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void reconstitutedCheckout_preservesOrderIdentityOnCompletionRetry() {
        CheckoutSession original = createCompletedGeneralAdmissionCheckout();
        CheckoutSession restored = new CheckoutSession(
            original.getId(),
            original.getStatus(),
            original.getOwnerId(),
            original.getEventId(),
            original.getInventoryMode(),
            new com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutItemCollection(original.getItems()),
            original.getPromotionId(),
            original.getDiscountAmount(),
            original.getTotalPrice(),
            original.getPaymentId()
        );
        assertEquals(Order.createFromCheckout(original).getId(), Order.createFromCheckout(restored).getId());
    }

    @Test
    void paidOrder_preservesNonzeroPricingAndPaymentSnapshot() {
        CheckoutSession checkout = new CheckoutSession(
            CheckoutSessionId.generate(),
            1L,
            UUID.randomUUID(),
            InventoryMode.GENERAL_ADMISSION
        );
        checkout.addItem(new GeneralAdmissionCheckoutItem(UUID.randomUUID(), Money.kzt(10000)));
        UUID promotionId = UUID.randomUUID();
        checkout.applyPromotion(promotionId, Money.kzt(2000));
        checkout.awaitPayment();
        UUID paymentId = UUID.randomUUID();
        checkout.confirmPayment(paymentId);

        Order order = Order.createFromCheckout(checkout);

        assertEquals(Money.kzt(10000), order.getTotalPrice());
        assertEquals(Money.kzt(2000), order.getDiscountAmount());
        assertEquals(Money.kzt(8000), order.getFinalPrice());
        assertEquals(paymentId, order.getPaymentId());
        assertEquals(promotionId, order.getPromotionId());
        assertEquals(Money.kzt(10000), ((GeneralAdmissionOrderItem) order.getItems().get(0)).getPrice());
    }
}
