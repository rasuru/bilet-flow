package com.biletflow.biletflow.paymentsmonetization.ohs;

import com.biletflow.biletflow.paymentsmonetization.ohs.dto.PaymentView;
import java.util.Optional;
import java.util.UUID;

public interface SupportActivationOperations {
    record Activation(String status, PaymentView payment) {}
    Optional<Activation> inspect(UUID eventId);
}

