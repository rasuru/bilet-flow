package com.biletflow.biletflow.eventmanagement.domain;

import java.util.Objects;

/**
 * Reference-data seat owned by Event Management.
 *
 * Ticket Inventory may reference SeatId but does not own location,
 * accessibility, or layout-category metadata.
 */
public record VenueSeat(SeatId id, SeatLocation location, boolean accessible, String priceCategory) {
    public VenueSeat {
        Objects.requireNonNull(id, "SeatId cannot be null");
        Objects.requireNonNull(location, "SeatLocation cannot be null");

        if (priceCategory != null) {
            priceCategory = priceCategory.trim();
            if (priceCategory.isEmpty()) {
                priceCategory = null;
            }
        }
    }

    public static VenueSeat create(SeatLocation location, boolean accessible, String priceCategory) {
        return new VenueSeat(SeatId.generate(), location, accessible, priceCategory);
    }
}
