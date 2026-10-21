package com.biletflow.biletflow.paymentsmonetization.infrastructure.payout;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.paymentsmonetization.application.PayoutNoticeSender;
import com.biletflow.biletflow.paymentsmonetization.application.view.PayoutView;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SmtpPayoutNoticeSender implements PayoutNoticeSender {

    private final EmailSender emails;

    public SmtpPayoutNoticeSender(EmailSender emails) {
        this.emails = Objects.requireNonNull(emails);
    }

    @Override
    public void send(PayoutView payout, String recipient) {
        String text =
            "Your simulated organizer payout is " +
            payout.status() +
            ".\n\n" +
            "Payout reference: " +
            payout.id() +
            "\nAmount: " +
            payout.amountKzt().toPlainString() +
            " KZT\n" +
            "Simulation reference: " +
            Objects.toString(payout.providerReference(), "none") +
            "\n" +
            "Processed at (UTC): " +
            payout.completedAt() +
            "\n" +
            (payout.failureReason() == null ? "" : "Reason: " + payout.failureReason() + "\n") +
            "\nThis is an internal BiletFlow simulation, not a Halyk bank transfer. No real money moved.\n" +
            "Failed payouts release their reservation. Submit a new request ID to try again after resolving the cause.";
        emails.send(
            new EmailSender.Email(
                payout.id(),
                recipient,
                "BiletFlow payout " + payout.status().toLowerCase(java.util.Locale.ROOT) + " (simulation)",
                text
            )
        );
    }
}
