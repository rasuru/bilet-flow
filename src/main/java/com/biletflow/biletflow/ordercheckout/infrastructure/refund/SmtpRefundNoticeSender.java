package com.biletflow.biletflow.ordercheckout.infrastructure.refund;

import com.biletflow.biletflow.common.notifications.EmailSender;
import com.biletflow.biletflow.ordercheckout.application.refund.RefundNotice;
import com.biletflow.biletflow.ordercheckout.application.refund.RefundNoticeSender;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SmtpRefundNoticeSender implements RefundNoticeSender {

    private final EmailSender emails;

    public SmtpRefundNoticeSender(EmailSender emails) {
        this.emails = Objects.requireNonNull(emails);
    }

    @Override
    public void send(RefundNotice notice, String verifiedRecipient) {
        String text =
            "Halyk has confirmed the full refund for this purchase.\n\n" +
            "Event: " +
            notice.title() +
            "\n" +
            "Order reference: " +
            notice.orderId() +
            "\n" +
            "Refund reference: " +
            notice.id() +
            "\n" +
            "Amount: " +
            notice.amountKzt().toPlainString() +
            " KZT\n" +
            "Confirmed at (UTC): " +
            notice.confirmedAt() +
            "\n\n" +
            "This was a Halyk sandbox transaction. No real money was charged or returned.\n" +
            "Any tickets issued for this purchase are refunded and cannot be used for admission.\n" +
            "The refund belongs to the original payment; claiming or transferring a ticket does not change its recipient.";
        emails.send(new EmailSender.Email(notice.id(), verifiedRecipient, "BiletFlow refund confirmed (sandbox)", text));
    }
}
