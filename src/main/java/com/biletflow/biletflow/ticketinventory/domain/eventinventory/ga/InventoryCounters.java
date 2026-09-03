package com.biletflow.biletflow.ticketinventory.domain.eventinventory.ga;

public record InventoryCounters(int totalCapacity, int reserved, int sold) {
    public InventoryCounters {
        if (totalCapacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative");
        }
        if (reserved < 0) {
            throw new IllegalArgumentException("Reserved count cannot be negative");
        }
        if (sold < 0) {
            throw new IllegalArgumentException("Sold count cannot be negative");
        }
        if (reserved + sold > totalCapacity) {
            throw new IllegalStateException("Reserved + sold cannot exceed total capacity");
        }
    }

    public static InventoryCounters initial(int totalCapacity) {
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        return new InventoryCounters(totalCapacity, 0, 0);
    }

    public int available() {
        return totalCapacity - reserved - sold;
    }

    public InventoryCounters withCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        if (newCapacity < reserved + sold) {
            throw new IllegalStateException("Capacity cannot be lower than reserved + sold inventory");
        }
        return new InventoryCounters(newCapacity, reserved, sold);
    }

    public InventoryCounters withReservation(int quantity) {
        validateQuantity(quantity);

        if (quantity > available()) {
            throw new IllegalStateException("Insufficient inventory available to reserve");
        }

        return new InventoryCounters(totalCapacity, reserved + quantity, sold);
    }

    public InventoryCounters withConfirmation(int quantity) {
        validateQuantity(quantity);

        if (quantity > reserved) {
            throw new IllegalStateException("Cannot confirm more than currently reserved inventory");
        }

        return new InventoryCounters(totalCapacity, reserved - quantity, sold + quantity);
    }

    public InventoryCounters withReservationRelease(int quantity) {
        validateQuantity(quantity);

        if (quantity > reserved) {
            throw new IllegalStateException("Cannot release more than currently reserved inventory");
        }

        return new InventoryCounters(totalCapacity, reserved - quantity, sold);
    }

    public InventoryCounters withSaleReversal(int quantity) {
        validateQuantity(quantity);

        if (quantity > sold) {
            throw new IllegalStateException("Cannot reverse more than currently sold inventory");
        }

        return new InventoryCounters(totalCapacity, reserved, sold - quantity);
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
    }
}
