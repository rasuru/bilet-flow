package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

public record CheckoutGeneralAdmissionSelection(int quantity) implements CheckoutHoldSelection {
    public CheckoutGeneralAdmissionSelection {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
    }
}
