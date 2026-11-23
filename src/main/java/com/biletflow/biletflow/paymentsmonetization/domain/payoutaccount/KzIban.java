package com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class KzIban {

    private static final Pattern FORMAT = Pattern.compile("KZ\\d{2}\\d{3}[A-Z0-9]{13}");
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private final String value;

    private KzIban(String value) {
        this.value = value;
    }

    public static KzIban parse(String raw) {
        Objects.requireNonNull(raw, "IBAN cannot be null");
        String normalized = raw.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(normalized).matches()) throw new IllegalArgumentException(
            "Enter a Kazakhstan IBAN: KZ followed by 18 characters"
        );
        if (!checksumValid(normalized)) throw new IllegalArgumentException("IBAN check digits do not match");
        return new KzIban(normalized);
    }

    private static boolean checksumValid(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder(rearranged.length() * 2);
        for (char c : rearranged.toCharArray()) digits.append(Character.digit(c, 36));
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }

    public String bankCode() {
        return value.substring(4, 7);
    }

    public String lastFour() {
        return value.substring(value.length() - 4);
    }

    public static String mask(String lastFour) {
        return "KZ** **** **** **** " + lastFour;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KzIban iban && iban.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return mask(lastFour());
    }
}
