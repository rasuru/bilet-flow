package com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount;

import java.time.Instant;
import java.util.Objects;

public class PayoutAccount {

    public static final int MAX_HOLDER_NAME = 100;

    private final Long organizerId;
    private final Instant registeredAt;
    private String holderName;
    private String bankCode;
    private String ibanLastFour;
    private Instant updatedAt;

    public PayoutAccount(
        Long organizerId,
        String holderName,
        String bankCode,
        String ibanLastFour,
        Instant registeredAt,
        Instant updatedAt
    ) {
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId cannot be null");
        this.holderName = Objects.requireNonNull(holderName, "holderName cannot be null");
        this.bankCode = Objects.requireNonNull(bankCode, "bankCode cannot be null");
        this.ibanLastFour = Objects.requireNonNull(ibanLastFour, "ibanLastFour cannot be null");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public static PayoutAccount register(Long organizerId, String holderName, KzIban iban, Instant now) {
        if (organizerId == null || organizerId <= 0) throw new IllegalArgumentException("Invalid organizer");
        Objects.requireNonNull(iban, "iban cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        return new PayoutAccount(organizerId, validHolder(holderName), iban.bankCode(), iban.lastFour(), now, now);
    }

    public void replace(String holderName, KzIban iban, Instant now) {
        Objects.requireNonNull(iban, "iban cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        this.holderName = validHolder(holderName);
        this.bankCode = iban.bankCode();
        this.ibanLastFour = iban.lastFour();
        this.updatedAt = now;
    }

    private static String validHolder(String holderName) {
        if (holderName == null || holderName.isBlank()) throw new IllegalArgumentException("Account holder name is required");
        String stripped = holderName.strip();
        if (stripped.length() > MAX_HOLDER_NAME) throw new IllegalArgumentException(
            "Account holder name must be at most " + MAX_HOLDER_NAME + " characters"
        );
        return stripped;
    }

    public String maskedIban() {
        return KzIban.mask(ibanLastFour);
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getIbanLastFour() {
        return ibanLastFour;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
