package com.biletflow.biletflow.paymentsmonetization.ohs;

import com.biletflow.biletflow.paymentsmonetization.ohs.dto.PaymentView;
import com.biletflow.biletflow.paymentsmonetization.ohs.dto.StartPaymentCommand;
import java.util.UUID;

/** Published PM contract; callers never use PM repositories or provider-specific types. */
public interface PaymentsOhs {
    boolean permitsPaidSales(UUID eventId);

    /** Checks activation under the same transaction lock used by paid checkout and suspension. */
    boolean permitsPaidSalesForCheckout(UUID eventId);

    PaymentView initiate(StartPaymentCommand command);
    PaymentView get(UUID paymentId);
    /** Checks the payment under its reconciliation lock before a direct administrative order refund. */
    void requireReadyForAdministrativeRefund(UUID paymentId);
    void abandon(UUID paymentId);
    UUID refund(UUID paymentId, String reason);
}
