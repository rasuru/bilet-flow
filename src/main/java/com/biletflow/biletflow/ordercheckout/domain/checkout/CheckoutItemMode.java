package com.biletflow.biletflow.ordercheckout.domain.checkout;

public sealed interface CheckoutItemMode permits GeneralAdmissionCheckoutItem, AssignedSeatingCheckoutItem {
    double getPrice();
}
