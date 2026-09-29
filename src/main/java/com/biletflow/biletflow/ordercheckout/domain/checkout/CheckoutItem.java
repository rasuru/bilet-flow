package com.biletflow.biletflow.ordercheckout.domain.checkout;

import com.biletflow.biletflow.common.domain.Money;

public sealed interface CheckoutItem permits GeneralAdmissionCheckoutItem, AssignedSeatingCheckoutItem {
    Money getPrice();
}
