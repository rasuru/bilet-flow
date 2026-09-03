package com.biletflow.biletflow.ticketinventory.domain.common;

import java.util.Objects;

public record SessionId(String value) {
    public SessionId {
        Objects.requireNonNull(value, "SessionId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SessionId cannot be blank");
        }
    }
}
