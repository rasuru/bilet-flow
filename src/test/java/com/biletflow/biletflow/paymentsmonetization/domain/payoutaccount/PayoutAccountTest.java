package com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PayoutAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    private static final KzIban IBAN = KzIban.parse("KZ86125KZT5004100100");

    @Test
    void registrationKeepsOnlyTheBankCodeAndLastFourCharacters() {
        var account = PayoutAccount.register(7L, "  Aigerim Sadykova ", IBAN, NOW);

        assertEquals(7L, account.getOrganizerId());
        assertEquals("Aigerim Sadykova", account.getHolderName());
        assertEquals("125", account.getBankCode());
        assertEquals("0100", account.getIbanLastFour());
        assertEquals("KZ** **** **** **** 0100", account.maskedIban());
        assertEquals(NOW, account.getRegisteredAt());
        assertEquals(NOW, account.getUpdatedAt());
    }

    @Test
    void replacementChangesDetailsButNotRegistrationTime() {
        var account = PayoutAccount.register(7L, "Old Name", IBAN, NOW);

        account.replace("New Name", IBAN, NOW.plusSeconds(60));

        assertEquals("New Name", account.getHolderName());
        assertEquals(NOW, account.getRegisteredAt());
        assertEquals(NOW.plusSeconds(60), account.getUpdatedAt());
    }

    @Test
    void holderNameAndOrganizerAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> PayoutAccount.register(7L, " ", IBAN, NOW));
        assertThrows(IllegalArgumentException.class, () -> PayoutAccount.register(7L, null, IBAN, NOW));
        assertThrows(IllegalArgumentException.class, () -> PayoutAccount.register(7L, "x".repeat(101), IBAN, NOW));
        assertThrows(IllegalArgumentException.class, () -> PayoutAccount.register(0L, "Holder", IBAN, NOW));
        assertDoesNotThrow(() -> PayoutAccount.register(7L, "x".repeat(100), IBAN, NOW));

        var account = PayoutAccount.register(7L, "Holder", IBAN, NOW);
        assertThrows(IllegalArgumentException.class, () -> account.replace("", IBAN, NOW));
        assertEquals("Holder", account.getHolderName());
    }
}
