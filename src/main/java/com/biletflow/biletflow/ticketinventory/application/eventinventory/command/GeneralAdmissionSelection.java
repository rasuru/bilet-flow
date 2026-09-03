package com.biletflow.biletflow.ticketinventory.application.eventinventory.command;

public record GeneralAdmissionSelection(int quantity) implements HoldSelection {
    public GeneralAdmissionSelection {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
    }
}
