package com.biletflow.biletflow.ordercheckout.domain.order;

public sealed interface OrderItem
        permits GeneralAdmissionOrderItem, AssignedSeatingOrderItem {}
