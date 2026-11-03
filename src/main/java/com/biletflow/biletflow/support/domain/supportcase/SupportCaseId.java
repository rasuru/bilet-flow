package com.biletflow.biletflow.support.domain.supportcase;

import java.util.Objects;
import java.util.UUID;

public record SupportCaseId(UUID value) {
    public SupportCaseId {
        Objects.requireNonNull(value, "SupportCaseId cannot be null");
    }

    public static SupportCaseId generate() {
        return new SupportCaseId(UUID.randomUUID());
    }
}
