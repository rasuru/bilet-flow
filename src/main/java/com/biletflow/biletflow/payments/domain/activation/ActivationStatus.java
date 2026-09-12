package com.biletflow.biletflow.payments.domain.activation;

public enum ActivationStatus {
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
