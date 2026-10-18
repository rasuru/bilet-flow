package com.biletflow.biletflow.ordercheckout.application;

import com.biletflow.biletflow.common.events.*;
import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentOutcome;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutcomeHandler implements IntegrationEventHandler {

    private final CheckoutService checkout;

    public PaymentOutcomeHandler(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @Override
    public String eventType() {
        return PaymentOutcome.TYPE;
    }

    @Override
    public void handle(IntegrationEvent event) {
        checkout.onOutcome(PaymentOutcome.from(event));
    }
}
