package com.biletflow.biletflow.paymentsmonetization.domain.payoutaccount;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class KzIbanTest {

    private static final String VALID = "KZ86125KZT5004100100";

    @Test
    void acceptsPrintedFormsAndExposesBankCodeAndLastFour() {
        var iban = KzIban.parse("kz86 125k zt50 0410 0100");

        assertEquals(KzIban.parse(VALID), iban);
        assertEquals("125", iban.bankCode());
        assertEquals("0100", iban.lastFour());
    }

    @Test
    void rejectsWrongCountryLengthOrCharacters() {
        assertThrows(IllegalArgumentException.class, () -> KzIban.parse("DE89370400440532013000"));
        assertThrows(IllegalArgumentException.class, () -> KzIban.parse("KZ86125KZT500410010"));
        assertThrows(IllegalArgumentException.class, () -> KzIban.parse("KZ86125KZT50041001000"));
        assertThrows(IllegalArgumentException.class, () -> KzIban.parse("KZ86ABCKZT5004100100"));
        assertThrows(IllegalArgumentException.class, () -> KzIban.parse("KZ86125KZT50041001-0"));
    }

    @Test
    void rejectsWrongCheckDigits() {
        var error = assertThrows(IllegalArgumentException.class, () -> KzIban.parse("KZ87125KZT5004100100"));
        assertTrue(error.getMessage().contains("check digits"));
        assertThrows(IllegalArgumentException.class, () -> KzIban.parse("KZ86125KZT5004100101"));
    }

    @Test
    void neverPrintsTheFullNumber() {
        var iban = KzIban.parse(VALID);

        assertEquals("KZ** **** **** **** 0100", iban.toString());
        assertFalse(iban.toString().contains("125KZT"));
    }
}
