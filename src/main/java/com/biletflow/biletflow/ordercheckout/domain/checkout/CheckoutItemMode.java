package com.biletflow.biletflow.ordercheckout.domain.checkout;

import com.biletflow.biletflow.common.domain.Money;

public sealed interface CheckoutItemMode permits GeneralAdmissionCheckoutItem, AssignedSeatingCheckoutItem {
    Money getPrice();
}
