package com.biletflow.biletflow.paymentsmonetization.application.view;

import java.util.UUID;

public record ActivationView(UUID eventId, String status, UUID paymentId, boolean sandbox, boolean verificationSimulated) {}
