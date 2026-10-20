package com.biletflow.biletflow.paymentsmonetization.application.view;

import java.util.UUID;

public record ActivationPaymentView(UUID paymentId, boolean sandbox, boolean verificationSimulated) {}
