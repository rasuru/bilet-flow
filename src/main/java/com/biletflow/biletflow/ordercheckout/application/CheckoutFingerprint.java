package com.biletflow.biletflow.ordercheckout.application;

import com.biletflow.biletflow.ordercheckout.application.command.CreateCheckoutCommand;

public interface CheckoutFingerprint {
    String of(CreateCheckoutCommand request);
}
