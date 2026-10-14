package com.biletflow.biletflow.ordercheckout.domain.checkout;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutSessionTest {

    private CheckoutSession createSession(InventoryMode mode) {
        return new CheckoutSession(CheckoutSessionId.generate(), 1L, UUID.randomUUID(), mode);
    }

    private GeneralAdmissionCheckoutItem generalAdmissionItem() {
        return new GeneralAdmissionCheckoutItem(UUID.randomUUID(), Money.zeroKzt());
    }

    private AssignedSeatingCheckoutItem assignedSeatingItem(UUID ticketTypeId, UUID seatId) {
        return new AssignedSeatingCheckoutItem(ticketTypeId, seatId, Money.zeroKzt());
    }

    @Test
    void newSession_startsInProgressWithZeroPrices() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

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
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        CheckoutItem item = generalAdmissionItem();

        session.addItem(item);

        assertEquals(1, session.getItems().size());
        assertSame(item, session.getItems().get(0));
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
    }

    @Test
    void addItem_generalAdmissionSession_rejectsAssignedSeatingItem() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(IllegalArgumentException.class, () -> session.addItem(assignedSeatingItem(UUID.randomUUID(), UUID.randomUUID())));

        assertTrue(session.getItems().isEmpty());
    }

    @Test
    void addItem_reservedSeatingSession_rejectsGeneralAdmissionItem() {
        CheckoutSession session = createSession(InventoryMode.RESERVED_SEATING);

        assertThrows(IllegalArgumentException.class, () -> session.addItem(generalAdmissionItem()));

        assertTrue(session.getItems().isEmpty());
    }

    @Test
    void addItem_nullItem_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(NullPointerException.class, () -> session.addItem(null));
    }

    @Test
    void completeFreeCheckout_withoutItems_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(IllegalStateException.class, session::completeFreeCheckout);

        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void completeFreeCheckout_withItems_completesSession() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        session.completeFreeCheckout();

        assertEquals(CheckoutSessionStatus.COMPLETED, session.getStatus());
        assertNull(session.getPaymentId());
    }

    @Test
    void completeFreeCheckout_cancelledSession_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.cancel();

        assertThrows(IllegalStateException.class, session::completeFreeCheckout);
    }

    @Test
    void cancel_inProgressSession_changesStatusToCancelled() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        session.cancel();

        assertEquals(CheckoutSessionStatus.CANCELLED, session.getStatus());
    }

    @Test
    void cancel_cancelledSession_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.cancel();

        assertThrows(IllegalStateException.class, session::cancel);
    }

    @Test
    void completedSession_cannotBeMutated() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());
        session.completeFreeCheckout();

        assertThrows(IllegalStateException.class, () -> session.addItem(generalAdmissionItem()));
        assertThrows(IllegalStateException.class, session::clearItems);
        assertThrows(IllegalStateException.class, session::removePromotion);
        assertThrows(IllegalStateException.class, () -> session.applyPromotion(UUID.randomUUID(), Money.zeroKzt()));
    }

    @Test
    void cancelledSession_cannotBeMutated() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.cancel();

        assertThrows(IllegalStateException.class, () -> session.addItem(generalAdmissionItem()));
        assertThrows(IllegalStateException.class, session::clearItems);
        assertThrows(IllegalStateException.class, session::removePromotion);
        assertThrows(IllegalStateException.class, () -> session.applyPromotion(UUID.randomUUID(), Money.zeroKzt()));
    }

    @Test
    void removeAssignedSeat_removesMatchingSeat() {
        CheckoutSession session = createSession(InventoryMode.RESERVED_SEATING);
        UUID ticketTypeId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        session.addItem(assignedSeatingItem(ticketTypeId, seatId));

        session.removeAssignedSeat(seatId);

        assertTrue(session.getItems().isEmpty());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
    }

    @Test
    void removeAssignedSeat_unknownSeat_throws() {
        CheckoutSession session = createSession(InventoryMode.RESERVED_SEATING);

        assertThrows(IllegalArgumentException.class, () -> session.removeAssignedSeat(UUID.randomUUID()));
    }

    @Test
    void removeAssignedSeat_wrongInventoryMode_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(IllegalStateException.class, () -> session.removeAssignedSeat(UUID.randomUUID()));
    }

    @Test
    void removeGeneralAdmissionItem_removesMatchingItem() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        UUID ticketTypeId = UUID.randomUUID();

        session.addItem(new GeneralAdmissionCheckoutItem(ticketTypeId, Money.zeroKzt()));

        session.removeGeneralAdmissionItem(ticketTypeId);

        assertTrue(session.getItems().isEmpty());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
    }

    @Test
    void removeGeneralAdmissionItem_unknownTicketType_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(IllegalArgumentException.class, () -> session.removeGeneralAdmissionItem(UUID.randomUUID()));
    }

    @Test
    void removeGeneralAdmissionItem_wrongInventoryMode_throws() {
        CheckoutSession session = createSession(InventoryMode.RESERVED_SEATING);

        assertThrows(IllegalStateException.class, () -> session.removeGeneralAdmissionItem(UUID.randomUUID()));
    }

    @Test
    void clearItems_removesItemsAndPromotion() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
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
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(NullPointerException.class, () -> session.applyPromotion(null, Money.zeroKzt()));
    }

    @Test
    void applyPromotion_nullDiscount_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(NullPointerException.class, () -> session.applyPromotion(UUID.randomUUID(), null));
    }

    @Test
    void removePromotion_resetsPromotionAndDiscount() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        session.applyPromotion(UUID.randomUUID(), Money.zeroKzt());
        session.removePromotion();

        assertNull(session.getPromotionId());
        assertEquals(Money.zeroKzt(), session.getDiscountAmount());
        assertEquals(Money.zeroKzt(), session.getFinalPrice());
    }

    @Test
    void awaitPayment_withoutItems_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(IllegalStateException.class, session::awaitPayment);
        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void awaitPayment_freeCheckout_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        assertThrows(IllegalStateException.class, session::awaitPayment);
        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void confirmPayment_whenNotAwaitingPayment_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(IllegalStateException.class, () -> session.confirmPayment(UUID.randomUUID()));
    }

    @Test
    void confirmPayment_nullPaymentId_throws() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);

        assertThrows(NullPointerException.class, () -> session.confirmPayment(null));
    }

    @Test
    void getItems_cannotBeModifiedThroughGetter() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(generalAdmissionItem());

        assertThrows(UnsupportedOperationException.class, () -> session.getItems().clear());
    }

    private CheckoutSession paidSession() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        session.addItem(new GeneralAdmissionCheckoutItem(UUID.randomUUID(), Money.kzt(10000)));
        return session;
    }

    @Test
    void addingItem_invalidatesPreviouslyCalculatedDiscount() {
        CheckoutSession session = paidSession();
        session.applyPromotion(UUID.randomUUID(), Money.kzt(2000));

        session.addItem(new GeneralAdmissionCheckoutItem(UUID.randomUUID(), Money.kzt(5000)));

        assertNull(session.getPromotionId());
        assertEquals(Money.zeroKzt(), session.getDiscountAmount());
        assertEquals(Money.kzt(15000), session.getFinalPrice());
    }

    @Test
    void removingItem_invalidatesDiscountEvenWhenBelowRemainingSubtotal() {
        CheckoutSession session = createSession(InventoryMode.GENERAL_ADMISSION);
        UUID removedType = UUID.randomUUID();
        session.addItem(new GeneralAdmissionCheckoutItem(removedType, Money.kzt(5000)));
        session.addItem(new GeneralAdmissionCheckoutItem(UUID.randomUUID(), Money.kzt(5000)));
        session.applyPromotion(UUID.randomUUID(), Money.kzt(2000));

        session.removeGeneralAdmissionItem(removedType);

        assertNull(session.getPromotionId());
        assertEquals(Money.zeroKzt(), session.getDiscountAmount());
        assertEquals(Money.kzt(5000), session.getFinalPrice());
    }

    @Test
    void removingAssignedSeat_invalidatesDiscount() {
        CheckoutSession session = createSession(InventoryMode.RESERVED_SEATING);
        UUID removedSeat = UUID.randomUUID();
        session.addItem(new AssignedSeatingCheckoutItem(UUID.randomUUID(), removedSeat, Money.kzt(5000)));
        session.addItem(new AssignedSeatingCheckoutItem(UUID.randomUUID(), UUID.randomUUID(), Money.kzt(5000)));
        session.applyPromotion(UUID.randomUUID(), Money.kzt(2000));

        session.removeAssignedSeat(removedSeat);

        assertNull(session.getPromotionId());
        assertEquals(Money.kzt(5000), session.getFinalPrice());
    }

    @Test
    void promotionCanBeReappliedAfterEditingItems() {
        CheckoutSession session = paidSession();
        session.applyPromotion(UUID.randomUUID(), Money.kzt(2000));
        session.addItem(new GeneralAdmissionCheckoutItem(UUID.randomUUID(), Money.kzt(5000)));
        UUID promotionId = UUID.randomUUID();

        session.applyPromotion(promotionId, Money.kzt(3000));

        assertEquals(promotionId, session.getPromotionId());
        assertEquals(Money.kzt(12000), session.getFinalPrice());
    }

    @Test
    void reconstitutionCopiesItemCollectionBeforeCheckoutCompletes() {
        CheckoutItemCollection supplied = new CheckoutItemCollection();
        supplied.addItem(generalAdmissionItem());
        CheckoutSession session = new CheckoutSession(
            CheckoutSessionId.generate(),
            CheckoutSessionStatus.IN_PROGRESS,
            1L,
            UUID.randomUUID(),
            InventoryMode.GENERAL_ADMISSION,
            supplied,
            null,
            Money.zeroKzt(),
            Money.zeroKzt(),
            null
        );
        session.completeFreeCheckout();

        supplied.clear();
        supplied.addItem(new GeneralAdmissionCheckoutItem(UUID.randomUUID(), Money.kzt(5000)));

        assertEquals(CheckoutSessionStatus.COMPLETED, session.getStatus());
        assertEquals(1, session.getItems().size());
        assertEquals(Money.zeroKzt(), session.getItems().get(0).getPrice());
        assertEquals(Money.zeroKzt(), session.getTotalPrice());
    }

    @Test
    void paidCheckout_completesWithConfirmedPaymentAndDiscountedPrice() {
        CheckoutSession session = paidSession();
        session.applyPromotion(UUID.randomUUID(), Money.kzt(2000));
        session.awaitPayment();
        assertEquals(CheckoutSessionStatus.AWAITING_PAYMENT, session.getStatus());
        UUID paymentId = UUID.randomUUID();

        session.confirmPayment(paymentId);

        assertEquals(CheckoutSessionStatus.COMPLETED, session.getStatus());
        assertEquals(paymentId, session.getPaymentId());
        assertEquals(Money.kzt(8000), session.getFinalPrice());
    }

    @Test
    void fullyDiscountedCheckout_completesWithoutPayment() {
        CheckoutSession session = paidSession();
        session.applyPromotion(UUID.randomUUID(), Money.kzt(10000));

        session.completeFreeCheckout();

        assertEquals(CheckoutSessionStatus.COMPLETED, session.getStatus());
        assertNull(session.getPaymentId());
    }

    @Test
    void nonFreeCheckout_cannotCompleteWithoutPayment() {
        CheckoutSession session = paidSession();
        assertThrows(IllegalStateException.class, session::completeFreeCheckout);
        assertEquals(CheckoutSessionStatus.IN_PROGRESS, session.getStatus());
    }

    @Test
    void discountExceedingSubtotal_isRejected() {
        CheckoutSession session = paidSession();
        assertThrows(IllegalArgumentException.class, () -> session.applyPromotion(UUID.randomUUID(), Money.kzt(10001)));
        assertNull(session.getPromotionId());
        assertEquals(Money.kzt(10000), session.getFinalPrice());
    }

    @Test
    void awaitingPayment_canBeCancelled() {
        CheckoutSession session = paidSession();
        session.awaitPayment();

        session.cancel();

        assertEquals(CheckoutSessionStatus.CANCELLED, session.getStatus());
        assertThrows(IllegalStateException.class, () -> session.confirmPayment(UUID.randomUUID()));
    }

    @Test
    void awaitingPayment_canExpireAndRejectLateConfirmation() {
        CheckoutSession session = paidSession();
        session.awaitPayment();

        session.expire();

        assertEquals(CheckoutSessionStatus.EXPIRED, session.getStatus());
        assertThrows(IllegalStateException.class, () -> session.confirmPayment(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, session::clearItems);
    }

    @Test
    void inProgressCheckout_canExpire() {
        CheckoutSession session = paidSession();
        session.expire();
        assertEquals(CheckoutSessionStatus.EXPIRED, session.getStatus());
        assertThrows(IllegalStateException.class, session::awaitPayment);
    }

    @Test
    void paymentFailure_terminatesCheckoutAndIsIdempotent() {
        CheckoutSession session = paidSession();
        session.awaitPayment();

        session.markPaymentFailed();
        session.markPaymentFailed();

        assertEquals(CheckoutSessionStatus.PAYMENT_FAILED, session.getStatus());
        assertNull(session.getPaymentId());
        assertThrows(IllegalStateException.class, () -> session.confirmPayment(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, session::awaitPayment);
        assertThrows(IllegalStateException.class, session::clearItems);
    }

    @Test
    void paymentFailure_requiresAwaitingPayment() {
        CheckoutSession session = paidSession();
        assertThrows(IllegalStateException.class, session::markPaymentFailed);
    }

    @Test
    void completedCheckout_cannotExpireOrFailOrBeCancelled() {
        CheckoutSession session = paidSession();
        session.awaitPayment();
        session.confirmPayment(UUID.randomUUID());
        assertThrows(IllegalStateException.class, session::expire);
        assertThrows(IllegalStateException.class, session::markPaymentFailed);
        assertThrows(IllegalStateException.class, session::cancel);
    }

    @Test
    void repeatedPaymentConfirmation_acceptsSameIdButRejectsDifferentId() {
        CheckoutSession session = paidSession();
        session.awaitPayment();
        UUID paymentId = UUID.randomUUID();
        session.confirmPayment(paymentId);

        assertDoesNotThrow(() -> session.confirmPayment(paymentId));
        assertThrows(IllegalStateException.class, () -> session.confirmPayment(UUID.randomUUID()));
        assertEquals(paymentId, session.getPaymentId());
    }
}
