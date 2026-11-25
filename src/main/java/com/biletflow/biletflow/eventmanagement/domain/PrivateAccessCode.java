package com.biletflow.biletflow.eventmanagement.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

public record PrivateAccessCode(String value) {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{16,64}");

    public PrivateAccessCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid private access code");
        }
    }

    public static PrivateAccessCode generate() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return new PrivateAccessCode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public boolean matches(String candidate) {
        if (candidate == null) return false;
        return MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), candidate.strip().getBytes(StandardCharsets.UTF_8));
    }
}
