package com.biletflow.biletflow.ordercheckout.domain.order;

import com.biletflow.biletflow.ordercheckout.domain.checkout.*;

public sealed interface OrderItemMode
        permits GeneralAdmissionOrderItem, AssignedSeatingOrderItem {

    boolean isUsed();

    public static OrderItemMode convertToOrderItem(CheckoutItemMode item) {
    if (item instanceof AssignedSeatingCheckoutItem assigned) {
        return new AssignedSeatingOrderItem(
                assigned.getTicketTypeId(),
                assigned.getSeatId(),
                assigned.getPrice()
        );
    }

    if (item instanceof GeneralAdmissionCheckoutItem general) {
        return new GeneralAdmissionOrderItem(
                general.getTicketTypeId(),
                general.getPrice()
        );
    }

    throw new IllegalArgumentException("Unsupported checkout item type");
}
}
