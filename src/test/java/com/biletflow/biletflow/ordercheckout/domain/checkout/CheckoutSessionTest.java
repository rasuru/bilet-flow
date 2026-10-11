
package com.biletflow.biletflow.ordercheckout.domain.checkout;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionTest {

    private CheckoutSession createSession(InventoryMode mode) {
        return new CheckoutSession(
            CheckoutSessionId.generate(),
            1L,
            UUID.randomUUID(),
            mode
        );
    }

    private GeneralAdmissionCheckoutItem generalAdmissionItem() {
        return new GeneralAdmissionCheckoutItem(
            UUID.randomUUID(),
            Money.zeroKzt()
        );
    }

    private AssignedSeatingCheckoutItem assignedSeatingItem(
        UUID ticketTypeId,
        UUID seatId
    ) {
        return new AssignedSeatingCheckoutItem(
            ticketTypeId,
            seatId,
            Money.zeroKzt()
        );
    }

    @Test
    void newSession_startsInProgressWithZeroPrices() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
        assertTrue(session.getItems().isEmpty());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
        assertEquals(Money.zeroKzt(), session.getDiscountAmount());
        assertEquals(Money.zeroKzt(), session.getFinalPrice());
        assertNull(session.getPaymentId());
        assertNull(session.getPromotionId());
    }

    @Test
    void addItem_generalAdmissionSession_acceptsGeneralAdmissionItem() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        CheckoutItem item = generalAdmissionItem();

        session.addItem(item);

        assertEquals(1, session.getItems().size());
        assertSame(item, session.getItems().get(0));
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
    }

    @Test
    void addItem_generalAdmissionSession_rejectsAssignedSeatingItem() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            IllegalArgumentException.class,
            () -> session.addItem(
                assignedSeatingItem(UUID.randomUUID(), UUID.randomUUID())
            )
        );

        assertTrue(session.getItems().isEmpty());
    }

    @Test
    void addItem_reservedSeatingSession_rejectsGeneralAdmissionItem() {
        CheckoutSession session =
            createSession(InventoryMode.RESERVED_SEATING);

        assertThrows(
            IllegalArgumentException.class,
            () -> session.addItem(generalAdmissionItem())
        );

        assertTrue(session.getItems().isEmpty());
    }

    @Test
    void addItem_nullItem_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            NullPointerException.class,
            () -> session.addItem(null)
        );
    }

    @Test
    void completeFreeCheckout_withoutItems_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            IllegalStateException.class,
            session::completeFreeCheckout
        );

        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void completeFreeCheckout_withItems_completesSession() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        session.completeFreeCheckout();

        assertEquals(CheckoutSessionStatus.COMPLETED, session.getStatus());
        assertNull(session.getPaymentId());
    }

    @Test
    void completeFreeCheckout_cancelledSession_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        session.cancel();

        assertThrows(
            IllegalStateException.class,
            session::completeFreeCheckout
        );
    }

    @Test
    void cancel_inProgressSession_changesStatusToCancelled() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        session.cancel();

        assertEquals(CheckoutSessionStatus.CANCELLED, session.getStatus());
    }

    @Test
    void cancel_cancelledSession_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        session.cancel();

        assertThrows(IllegalStateException.class, session::cancel);
    }

    @Test
    void completedSession_cannotBeMutated() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());
        session.completeFreeCheckout();

        assertThrows(
            IllegalStateException.class,
            () -> session.addItem(generalAdmissionItem())
        );
        assertThrows(
            IllegalStateException.class,
            session::clearItems
        );
        assertThrows(
            IllegalStateException.class,
            session::removePromotion
        );
        assertThrows(
            IllegalStateException.class,
            () -> session.applyPromotion(UUID.randomUUID(), Money.zeroKzt())
        );
    }

    @Test
    void cancelledSession_cannotBeMutated() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        session.cancel();

        assertThrows(
            IllegalStateException.class,
            () -> session.addItem(generalAdmissionItem())
        );
        assertThrows(
            IllegalStateException.class,
            session::clearItems
        );
        assertThrows(
            IllegalStateException.class,
            session::removePromotion
        );
        assertThrows(
            IllegalStateException.class,
            () -> session.applyPromotion(UUID.randomUUID(), Money.zeroKzt())
        );
    }

    @Test
    void removeAssignedSeat_removesMatchingSeat() {
        CheckoutSession session =
            createSession(InventoryMode.RESERVED_SEATING);
        UUID ticketTypeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        session.addItem(assignedSeatingItem(ticketTypeId, seatId));

        session.removeAssignedSeat(seatId);

        assertTrue(session.getItems().isEmpty());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
    }

    @Test
    void removeAssignedSeat_unknownSeat_throws() {
        CheckoutSession session =
            createSession(InventoryMode.RESERVED_SEATING);

        assertThrows(
            IllegalArgumentException.class,
            () -> session.removeAssignedSeat(UUID.randomUUID())
        );
    }

    @Test
    void removeAssignedSeat_wrongInventoryMode_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            IllegalStateException.class,
            () -> session.removeAssignedSeat(UUID.randomUUID())
        );
    }

    @Test
    void removeGeneralAdmissionItem_removesMatchingItem() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        UUID ticketTypeId = UUID.randomUUID();

        session.addItem(
            new GeneralAdmissionCheckoutItem(ticketTypeId, Money.zeroKzt())
        );

        session.removeGeneralAdmissionItem(ticketTypeId);

        assertTrue(session.getItems().isEmpty());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
    }

    @Test
    void removeGeneralAdmissionItem_unknownTicketType_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            IllegalArgumentException.class,
            () -> session.removeGeneralAdmissionItem(UUID.randomUUID())
        );
    }

    @Test
    void removeGeneralAdmissionItem_wrongInventoryMode_throws() {
        CheckoutSession session =
            createSession(InventoryMode.RESERVED_SEATING);

        assertThrows(
            IllegalStateException.class,
            () -> session.removeGeneralAdmissionItem(UUID.randomUUID())
        );
    }

    @Test
    void clearItems_removesItemsAndPromotion() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        UUID promotionId = UUID.randomUUID();
        session.applyPromotion(promotionId, Money.zeroKzt());

        session.clearItems();

        assertTrue(session.getItems().isEmpty());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
        assertEquals(Money.zeroKzt(), session.getDiscountAmount());
        assertEquals(Money.zeroKzt(), session.getFinalPrice());
        assertNull(session.getPromotionId());
    }

    @Test
    void applyPromotion_nullPromotionId_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            NullPointerException.class,
            () -> session.applyPromotion(null, Money.zeroKzt())
        );
    }

    @Test
    void applyPromotion_nullDiscount_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            NullPointerException.class,
            () -> session.applyPromotion(UUID.randomUUID(), null)
        );
    }

    @Test
    void removePromotion_resetsPromotionAndDiscount() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        session.applyPromotion(UUID.randomUUID(), Money.zeroKzt());
        session.removePromotion();

        assertNull(session.getPromotionId());
        assertEquals(Money.zeroKzt(), session.getDiscountAmount());
        assertEquals(Money.zeroKzt(), session.getFinalPrice());
    }

    @Test
    void awaitPayment_withoutItems_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(IllegalStateException.class, session::awaitPayment);
        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void awaitPayment_freeCheckout_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        assertThrows(IllegalStateException.class, session::awaitPayment);
        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void confirmPayment_whenNotAwaitingPayment_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            IllegalStateException.class,
            () -> session.confirmPayment(UUID.randomUUID())
        );
    }

    @Test
    void confirmPayment_nullPaymentId_throws() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            NullPointerException.class,
            () -> session.confirmPayment(null)
        );
    }

    @Test
    void getItems_cannotBeModifiedThroughGetter() {
        CheckoutSession session =
            createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        assertThrows(
            UnsupportedOperationException.class,
            () -> session.getItems().clear()
        );
    }
}
