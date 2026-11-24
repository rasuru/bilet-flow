package com.biletflow.biletflow.eventmanagement.domain;

import java.util.Objects;

/** Optional stage or arena rectangle in the layout coordinate space. */
public record VenueLayoutFocus(String label, int x, int y, int width, int height) {
    public VenueLayoutFocus {
        Objects.requireNonNull(label, "label cannot be null");
        if (label.isBlank() || x < 0 || y < 0 || width <= 0 || height <= 0 || width > 800 - x || height > 600 - y) {
            throw new IllegalArgumentException("Invalid layout focal area");
        }
    }
}

