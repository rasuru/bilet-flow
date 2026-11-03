package com.biletflow.biletflow.paymentsmonetization.application.view;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


//The organizer's paid-sales activation checklist.

public record ActivationChecklistView(
    UUID eventId,
    String status,
    List<Item> items,
    boolean readyToRequest,
    boolean complete,
    BigDecimal activationFeeKzt,
    boolean sandbox
) {
    public record Item(String key, boolean done, boolean simulated, String description) {}

    public ActivationChecklistView {
        items = List.copyOf(items);
    }
}
