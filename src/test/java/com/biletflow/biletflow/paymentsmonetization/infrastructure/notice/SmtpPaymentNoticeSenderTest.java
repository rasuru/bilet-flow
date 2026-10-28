package com.biletflow.biletflow.paymentsmonetization.infrastructure.notice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentNoticeStore.PaymentNotice;
import com.biletflow.biletflow.paymentsmonetization.domain.notice.PaymentNoticeKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class SmtpPaymentNoticeSenderTest {

    @ParameterizedTest
    @EnumSource(PaymentNoticeKind.class)
    void failureEmailNamesTheEventAmountAndReferenceAndIsLabelledSandbox(PaymentNoticeKind kind) {
        var emails = mock(EmailSender.class);
        var notice = new PaymentNotice(
            UUID.randomUUID(),
            kind,
            7,
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("1000.00"),
            Instant.EPOCH
        );

        new SmtpPaymentNoticeSender(emails).send(notice, "Spring Concert", "payer@example.com");

        var sent = ArgumentCaptor.forClass(EmailSender.Email.class);
        verify(emails).send(sent.capture());
        assertEquals(notice.id(), sent.getValue().id());
        assertEquals("payer@example.com", sent.getValue().recipient());
        assertTrue(sent.getValue().subject().contains("Spring Concert"));
        assertTrue(sent.getValue().text().contains("1000.00 KZT"));
        assertTrue(sent.getValue().text().contains(notice.paymentId().toString()));
        assertTrue(sent.getValue().text().contains("No real money moved"));
        assertNull(sent.getValue().attachment());
    }
}
