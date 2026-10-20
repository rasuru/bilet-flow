package com.biletflow.biletflow.ordercheckout.infrastructure.refund;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.ordercheckout.application.refund.RefundNotice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SmtpRefundNoticeSenderTest {

    @Test
    void receiptIdentifiesFullSandboxRefundWithoutAnAdmissionAttachment() {
        var emails = mock(EmailSender.class);
        var n = new RefundNotice(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            42,
            "Concert",
            new BigDecimal("1234.50"),
            Instant.parse("2026-10-20T12:00:00Z")
        );
        new SmtpRefundNoticeSender(emails).send(n, "buyer@example.com");
        var sent = ArgumentCaptor.forClass(EmailSender.Email.class);
        verify(emails).send(sent.capture());
        var email = sent.getValue();
        assertEquals(n.id(), email.id());
        assertEquals("buyer@example.com", email.recipient());
        assertTrue(email.subject().contains("sandbox"));
        assertTrue(email.text().contains("Concert"));
        assertTrue(email.text().contains("1234.50 KZT"));
        assertTrue(email.text().contains(n.orderId().toString()));
        assertTrue(email.text().contains(n.id().toString()));
        assertTrue(email.text().contains("No real money"));
        assertTrue(email.text().contains("cannot be used for admission"));
        assertNull(email.attachment());
    }
}
