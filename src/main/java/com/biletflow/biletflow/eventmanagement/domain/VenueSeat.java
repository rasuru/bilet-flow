package com.biletflow.biletflow.eventmanagement.domain;

import java.util.Objects;

/**
 * Reference-data seat owned by Event Management.
 *
 * Ticket Inventory may reference SeatId but does not own location,
 * or layout-category metadata.
 */
public record VenueSeat(SeatId id, SeatLocation location, String priceCategory, SeatPosition position) {
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

    public VenueSeat(SeatId id, SeatLocation location, String priceCategory) {
        this(id, location, priceCategory, null);
    }

    public static VenueSeat create(SeatLocation location, String priceCategory) {
        return new VenueSeat(SeatId.generate(), location, priceCategory);
    }
}

