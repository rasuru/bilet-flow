package com.biletflow.biletflow.paymentsmonetization.domain.activation;

public enum EventPaidSalesActivationStatus {
    PENDING,
    ACTIVE,
    SUSPENDED;

    /**
     * True only when paid ticket sales are permitted for the event.
     * SUSPENDED blocks sales exactly as PENDING does.
     */
    public boolean permitsPaidSales() {
        return this == ACTIVE;
    }
}
