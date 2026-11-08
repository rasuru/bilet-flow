package com.biletflow.biletflow.promotions.domain.promocode;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

public record CodeValue(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9]{6,20}");

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int GENERATED_LENGTH = 8;
    private static final RandomGenerator RANDOM = new SecureRandom();

    public CodeValue {
        Objects.requireNonNull(value, "Promo code cannot be null");
        value = value.trim().toUpperCase(Locale.ROOT);

        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Promo code must be 6-20 letters or digits");
        }
    }

    public static CodeValue generate() {
        return generate(RANDOM);
    }

    static CodeValue generate(RandomGenerator random) {
        StringBuilder code = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new CodeValue(code.toString());
    }
}
