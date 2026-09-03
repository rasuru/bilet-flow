package com.biletflow.biletflow.eventmanagement.domain;

import java.util.Objects;

/**
 * Event venue configuration.
 *
 * Structural seat metadata belongs to VenueLayout. Assigned-seating venues
 * reference a predefined layout by VenueLayoutId.
 */
public class Venue {

    private String name;
    private String address;
    private int totalCapacity;
    private SeatingConfig seatingConfig;

    public sealed interface SeatingConfig permits GeneralAdmission, AssignedSeating {}

    public record GeneralAdmission() implements SeatingConfig {}

    public record AssignedSeating(VenueLayoutId layoutId) implements SeatingConfig {
        public AssignedSeating {
            Objects.requireNonNull(layoutId, "VenueLayoutId cannot be null for assigned seating");
        }
    }

    public static Venue createGeneralAdmission(String name, String address, int capacity) {
        return new Venue(name, address, capacity, new GeneralAdmission());
    }

    public static Venue createAssignedSeating(String name, String address, int capacity, VenueLayoutId layoutId) {
        return new Venue(name, address, capacity, new AssignedSeating(layoutId));
    }

    private Venue(String name, String address, int capacity, SeatingConfig seatingConfig) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Venue capacity must be greater than zero");
        }

        this.name = validateNotBlank(name, "Venue name");
        this.address = validateNotBlank(address, "Venue address");
        this.totalCapacity = capacity;
        this.seatingConfig = Objects.requireNonNull(seatingConfig, "Seating config cannot be null");
    }

    public boolean isAssignedSeating() {
        return seatingConfig instanceof AssignedSeating;
    }

    public void updateDetails(String name, String address, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Venue capacity must be greater than zero");
        }

        this.name = validateNotBlank(name, "Venue name");
        this.address = validateNotBlank(address, "Venue address");
        this.totalCapacity = capacity;
    }

    public Venue copy() {
        return new Venue(name, address, totalCapacity, seatingConfig);
    }

    private static String validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }

        return value.trim();
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public SeatingConfig getSeatingConfig() {
        return seatingConfig;
    }
}
