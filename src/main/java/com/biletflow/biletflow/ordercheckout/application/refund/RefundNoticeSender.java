package com.biletflow.biletflow.ordercheckout.application.refund;

/** Successful return means transport acceptance; failures propagate to the durable worker. */
public interface RefundNoticeSender {
    void send(RefundNotice notice, String verifiedRecipient);
}
