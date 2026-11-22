package com.biletflow.biletflow.common.application;

public record QueryPage(int offset, int limit) {
    public QueryPage {
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Offset must be non-negative and limit must be between 1 and 100");
        }
    }
}
