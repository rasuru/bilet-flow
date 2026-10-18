package com.biletflow.biletflow.ordercheckout.domain.order;

public sealed interface OrderItem permits GeneralAdmissionOrderItem, AssignedSeatingOrderItem {
    java.util.UUID getTicketTypeId();
    com.biletflow.biletflow.common.domain.Money getPrice();
}
