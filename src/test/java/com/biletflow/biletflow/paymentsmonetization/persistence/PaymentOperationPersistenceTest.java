package com.biletflow.biletflow.paymentsmonetization.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperation;
import com.biletflow.biletflow.paymentsmonetization.persistence.payment.PaymentOperationPersistence;
import org.junit.jupiter.api.Test;

class PaymentOperationPersistenceTest {

    @Test
    void existingDatabaseOperationCodesRoundTripWithoutChangingMeaning() {
        var converter = new PaymentOperationPersistence();
        for (var operation : PaymentOperation.values())
            assertEquals(operation, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(operation)));
        assertEquals("charge", converter.convertToDatabaseColumn(PaymentOperation.CAPTURE));
        assertEquals("cancel", converter.convertToDatabaseColumn(PaymentOperation.VOID));
        assertEquals("refund", converter.convertToDatabaseColumn(PaymentOperation.REFUND));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("capture"));
    }
}
