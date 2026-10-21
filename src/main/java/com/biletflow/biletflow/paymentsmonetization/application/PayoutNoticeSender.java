package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.application.view.PayoutView;

public interface PayoutNoticeSender {
    void send(PayoutView payout, String verifiedRecipient);
}
