package com.biletflow.biletflow.paymentsmonetization.infrastructure.payout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.paymentsmonetization.application.view.PayoutView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class SmtpPayoutNoticeSenderTest {

    @ParameterizedTest
    @ValueSource(strings = { "COMPLETED", "FAILED" })
    void identifiesInternalSimulationAndIncludesStablePayoutReferences(String status) {
        var emails = mock(EmailSender.class);
        var id = UUID.randomUUID();
        var payout = new PayoutView(
            id,
            new BigDecimal("970.00"),
            "KZT",
            status,
            Instant.EPOCH,
            Instant.EPOCH,
            status.equals("COMPLETED") ? "SIM-" + id : null,
            status.equals("FAILED") ? "Simulated rejection" : null,
            true
        );
        new SmtpPayoutNoticeSender(emails).send(payout, "organizer@example.com");
        var sent = ArgumentCaptor.forClass(EmailSender.Email.class);
        verify(emails).send(sent.capture());
        assertEquals(id, sent.getValue().id());
        assertTrue(sent.getValue().text().contains("970.00 KZT"));
        assertTrue(sent.getValue().text().contains(id.toString()));
        assertTrue(sent.getValue().text().contains("No real money moved"));
        assertTrue(sent.getValue().text().contains(status));
        assertNull(sent.getValue().attachment());
    }
}
