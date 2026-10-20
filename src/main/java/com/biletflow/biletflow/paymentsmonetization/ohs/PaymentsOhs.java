package com.biletflow.biletflow.paymentsmonetization.ohs;

import com.biletflow.biletflow.paymentsmonetization.ohs.dto.PaymentView;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.StartPaymentCommand;
import java.util.UUID;

/** Published PM contract; callers never use PM repositories or provider-specific types. */
public interface PaymentsOhs {
    PaymentView initiate(StartPaymentCommand command);
    PaymentView get(UUID paymentId);
    void abandon(UUID paymentId);
    UUID refund(UUID paymentId, String reason);
}
