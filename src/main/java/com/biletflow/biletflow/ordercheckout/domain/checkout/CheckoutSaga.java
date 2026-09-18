package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.util.UUID;
import enums.*;

public class CheckoutSaga {
    private final UUID checkoutId;

    private CheckoutSagaStatus status;
    private CheckoutStep currentStep;

    private TicketReservationStatus ticketReservationStatus;
    private PromotionStatus promotionStatus;
    private PaymentStatus paymentStatus;

    public CheckoutSaga(UUID checkoutId) {
        this.checkoutId = checkoutId;
        this.status = CheckoutSagaStatus.IN_PROGRESS;
        this.currentStep = CheckoutStep.SELECT_TICKETS;
        this.ticketReservationStatus = TicketReservationStatus.PENDING; 
        this.promotionStatus = PromotionStatus.PENDING;
        this.paymentStatus = PaymentStatus.PENDING;
    }

    // Methods to progress through the checkout steps
    // Ticket Selection
    public void completeTicketSelection() {
        if (currentStep != CheckoutStep.SELECT_TICKETS) {
            throw new IllegalStateException("Cannot complete ticket selection at this step!");
        }
        this.currentStep = CheckoutStep.RESERVE_TICKETS;
        this.ticketReservationStatus = TicketReservationStatus.PENDING;
    }

    public void failTicketSelection() {
        if (currentStep != CheckoutStep.SELECT_TICKETS) {
            throw new IllegalStateException("Cannot fail ticket selection at this step!");
        }
        this.status = CheckoutSagaStatus.FAILED;
    }

    // Ticket Reservation
    public void completeTicketReservation() {
        if (currentStep != CheckoutStep.RESERVE_TICKETS) {
            throw new IllegalStateException("Cannot complete ticket reservation at this step!");
        }
        this.currentStep = CheckoutStep.APPLY_PROMOTION;
        this.ticketReservationStatus = TicketReservationStatus.SUCCESS;
    }

    public void failTicketReservation() {
        if (currentStep != CheckoutStep.RESERVE_TICKETS) {
            throw new IllegalStateException("Cannot fail ticket reservation at this step!");
        }
        this.status = CheckoutSagaStatus.FAILED;
        this.ticketReservationStatus = TicketReservationStatus.FAILED;
    }

    // Promotion Application
    public void completePromotionApplication() {
        if (currentStep != CheckoutStep.APPLY_PROMOTION) {
            throw new IllegalStateException("Cannot complete promotion application at this step!");
        }
        this.currentStep = CheckoutStep.PROCESS_PAYMENT;
        this.promotionStatus = PromotionStatus.SUCCESS;
    }
    
    public void skipPromotion() {
        if (currentStep != CheckoutStep.APPLY_PROMOTION) {
            throw new IllegalStateException("Cannot skip promotion at this step!");
        }
        this.currentStep = CheckoutStep.PROCESS_PAYMENT;
        this.promotionStatus = PromotionStatus.SKIPPED;
    }

    public void failPromotionApplication() {
        if (currentStep != CheckoutStep.APPLY_PROMOTION) {
            throw new IllegalStateException("Cannot fail promotion application at this step!");
        }
        this.status = CheckoutSagaStatus.FAILED;
        this.promotionStatus = PromotionStatus.FAILED;
    }

    // Payment Processing
    public void completePayment() {
        if (currentStep != CheckoutStep.PROCESS_PAYMENT) {
            throw new IllegalStateException("Cannot complete payment at this step!");
        }
        this.status = CheckoutSagaStatus.COMPLETED;
        this.currentStep = CheckoutStep.CREATE_ORDER;
        this.paymentStatus = PaymentStatus.SUCCESS;
    }

    public void failPayment() {
        if (currentStep != CheckoutStep.PROCESS_PAYMENT) {
            throw new IllegalStateException("Cannot fail payment at this step!");
        }
        this.status = CheckoutSagaStatus.FAILED;
        this.paymentStatus = PaymentStatus.FAILED;
    }

    // Getters
    public CheckoutSagaStatus getStatus() {
        return status;
    }

    public CheckoutStep getCurrentStep() {
        return currentStep;
    }

    public TicketReservationStatus getTicketReservationStatus() {
        return ticketReservationStatus;
    }

    public PromotionStatus getPromotionStatus() {
        return promotionStatus;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public UUID getCheckoutId() {
        return checkoutId;
    }
}
