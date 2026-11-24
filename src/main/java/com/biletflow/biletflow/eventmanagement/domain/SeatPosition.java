package com.biletflow.biletflow.eventmanagement.domain;

/** Seat center in the layout's 800 by 600 logical coordinate space. */
public record SeatPosition(int x, int y) {
    public SeatPosition {
        if (x < 0 || x > 800 || y < 0 || y > 600) {
            throw new IllegalArgumentException("Seat position is outside the layout");
        }
    }
}

