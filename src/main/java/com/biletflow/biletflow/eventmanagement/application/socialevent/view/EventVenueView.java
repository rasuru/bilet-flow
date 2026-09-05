package com.biletflow.biletflow.eventmanagement.application.socialevent.view;

import java.util.UUID;

public record EventVenueView(String name, String address, int totalCapacity, SeatingMode seatingMode, UUID venueLayoutId) {
    public enum SeatingMode {
        GENERAL_ADMISSION,
        ASSIGNED_SEATING,
    }
}
