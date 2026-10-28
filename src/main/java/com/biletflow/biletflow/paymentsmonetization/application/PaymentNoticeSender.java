package com.biletflow.biletflow.paymentsmonetization.application;

public interface PaymentNoticeSender {
    void send(PaymentNoticeStore.PaymentNotice notice, String eventTitle, String verifiedRecipient);
}
