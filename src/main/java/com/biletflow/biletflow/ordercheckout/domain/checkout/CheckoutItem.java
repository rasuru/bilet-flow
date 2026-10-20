package com.biletflow.biletflow.ordercheckout.domain.checkout;

import com.biletflow.biletflow.common.domain.Money;
import java.util.UUID;

public sealed interface CheckoutItem permits GeneralAdmissionCheckoutItem, AssignedSeatingCheckoutItem {
    Money getPrice();

    UUID getTicketTypeId();
}
