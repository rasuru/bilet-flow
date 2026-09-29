package com.biletflow.biletflow.ordercheckout.domain.checkout;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionTest {

    private CheckoutSession createSession(InventoryMode inventoryMode) {
        return new CheckoutSession(
            CheckoutSessionId.generate(),
            1L,
            UUID.randomUUID(),
            inventoryMode
        );
    }

    private GeneralAdmissionCheckoutItem generalAdmissionItem() {
        return new GeneralAdmissionCheckoutItem(
            UUID.randomUUID(),
            Money.zeroKzt()
        );
    }

    private AssignedSeatingCheckoutItem assignedSeatingItem() {
        return new AssignedSeatingCheckoutItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Money.zeroKzt()
        );
    }

    @Test
    void newSession_startsInProgress() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
        assertTrue(session.getItems().isEmpty());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
        assertEquals(Money.zeroKzt(), session.getFinalPrice());
    }

    @Test
    void addItem_generalAdmissionSession_acceptsGeneralAdmissionItem() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        CheckoutItem item = generalAdmissionItem();

        session.addItem(item);

        assertEquals(1, session.getItems().size());
        assertSame(item, session.getItems().get(0));
    }

    @Test
    void addItem_generalAdmissionSession_rejectsAssignedSeatingItem() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            IllegalArgumentException.class,
            () -> session.addItem(assignedSeatingItem())
        );

        assertTrue(session.getItems().isEmpty());
    }

    @Test
    void addItem_reservedSeatingSession_rejectsGeneralAdmissionItem() {
        CheckoutSession session = createSession(InventoryMode.RESERVED_SEATING);

        assertThrows(
            IllegalArgumentException.class,
            () -> session.addItem(generalAdmissionItem())
        );

        assertTrue(session.getItems().isEmpty());
    }

    @Test
    void complete_withoutItems_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(IllegalStateException.class, session::complete);

        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void complete_withItems_changesStatusToCompleted() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        session.complete();

        assertEquals(CheckoutSessionStatus.COMPLETED, session.getStatus());
    }

    @Test
    void cancel_changesStatusToCancelled() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        session.cancel();

        assertEquals(CheckoutSessionStatus.CANCELLED, session.getStatus());
    }

    @Test
    void completedSession_cannotBeCancelled() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());
        session.complete();

        assertThrows(IllegalStateException.class, session::cancel);
    }

    @Test
    void cancelledSession_cannotBeCompleted() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.cancel();

        assertThrows(IllegalStateException.class, session::complete);
    }

    @Test
    void completedSession_cannotBeMutated() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());
        session.complete();

        assertThrows(
            IllegalStateException.class,
            () -> session.addItem(generalAdmissionItem())
        );
        assertThrows(
            IllegalStateException.class,
            () -> session.clearItems()
        );
        assertThrows(
            IllegalStateException.class,
            () -> session.removePromotion()
        );
        assertThrows(
            IllegalStateException.class,
            () -> session.setPaymentId(UUID.randomUUID())
        );
    }

    @Test
    void cancelledSession_cannotBeMutated() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.cancel();

        assertThrows(
            IllegalStateException.class,
            () -> session.addItem(generalAdmissionItem())
        );
        assertThrows(
            IllegalStateException.class,
            () -> session.clearItems()
        );
        assertThrows(
            IllegalStateException.class,
            () -> session.removePromotion()
        );
        assertThrows(
            IllegalStateException.class,
            () -> session.setPaymentId(UUID.randomUUID())
        );
    }

    @Test
    void removeItem_existingItem_removesIt() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        CheckoutItem item = generalAdmissionItem();

        session.addItem(item);
        session.removeItem(item);

        assertTrue(session.getItems().isEmpty());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
        assertEquals(Money.zeroKzt(), session.getFinalPrice());
    }

    @Test
    void removeItem_unknownItem_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            IllegalArgumentException.class,
            () -> session.removeItem(generalAdmissionItem())
        );
    }

    @Test
    void clearItems_removesAllItemsAndResetsPrices() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        session.clearItems();

        assertTrue(session.getItems().isEmpty());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
        assertEquals(Money.zeroKzt(), session.getFinalPrice());
    }

    @Test
    void applyPromotion_withNullPromotionId_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            NullPointerException.class,
            () -> session.applyPromotion(null, Money.zeroKzt())
        );
    }

    @Test
    void applyPromotion_withNullDiscount_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            NullPointerException.class,
            () -> session.applyPromotion(UUID.randomUUID(), null)
        );
    }

    @Test
    void setPaymentId_withNullId_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(
            NullPointerException.class,
            () -> session.setPaymentId(null)
        );
    }

    @Test
    void itemsCollection_cannotBeModifiedThroughGetter() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        assertThrows(
            UnsupportedOperationException.class,
            () -> session.getItems().clear()
        );
    }
}
