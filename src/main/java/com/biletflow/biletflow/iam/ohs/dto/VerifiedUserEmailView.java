package com.biletflow.biletflow.iam.ohs.dto;

import java.util.Locale;
import java.util.Objects;

public record VerifiedUserEmailView(Long userId, String email) {
    public VerifiedUserEmailView {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(email, "email cannot be null");

        email = email.trim().toLowerCase(Locale.ROOT);

        if (email.isBlank()) {
            throw new IllegalArgumentException("email cannot be blank");
        }
    }
}
