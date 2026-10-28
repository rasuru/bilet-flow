package com.biletflow.biletflow.paymentsmonetization.infrastructure.notice;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentNoticeSender;
import com.biletflow.biletflow.paymentsmonetization.application.PaymentNoticeStore.PaymentNotice;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Plain-text payment-failure emails. The notice ID is the stable Message-ID, so an at-least-once resend is recognizable. */
@Component
public class SmtpPaymentNoticeSender implements PaymentNoticeSender {

    private final EmailSender emails;

    public SmtpPaymentNoticeSender(EmailSender emails) {
        this.emails = Objects.requireNonNull(emails);
    }

    @Override
    public void send(PaymentNotice notice, String eventTitle, String recipient) {
        String subject;
        String body;
        switch (notice.kind()) {
            case TICKET_PAYMENT_FAILED -> {
                subject = "Payment not completed for " + eventTitle;
                body =
                    "Your payment for " +
                    eventTitle +
                    " was declined or cancelled by the bank. No tickets were issued and no charge was confirmed.\n" +
                    "You can start a new checkout if tickets are still available.";
            }
            case ACTIVATION_FEE_FAILED -> {
                subject = "Paid sales activation fee not completed for " + eventTitle;
                body =
                    "The paid-sales activation fee for " +
                    eventTitle +
                    " was declined or cancelled by the bank. Paid sales are not active.\n" +
                    "Request activation again to receive a new fee payment.";
            }
            default -> throw new IllegalArgumentException("Unsupported notice kind " + notice.kind());
        }
        String text =
            body +
            "\n\nAmount: " +
            notice.amountKzt().toPlainString() +
            " KZT\nPayment reference: " +
            notice.paymentId() +
            "\n\nThis is a Halyk sandbox transaction in a BiletFlow demonstration. No real money moved.";
        emails.send(new EmailSender.Email(notice.id(), recipient, "BiletFlow: " + subject, text));
    }
}
