package com.biletflow.biletflow.ordercheckout.domain.order;

import com.biletflow.biletflow.common.domain.Money;
import java.util.UUID;

public sealed interface OrderItem permits GeneralAdmissionOrderItem, AssignedSeatingOrderItem {
    UUID getTicketTypeId();
    Money getPrice();
}
