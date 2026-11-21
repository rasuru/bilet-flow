package com.biletflow.biletflow.ordercheckout.application;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.ordercheckout.domain.order.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OrderAdminQueries {
    record Filter(UUID orderId, UUID eventId, Long ownerId, OrderStatus status, UUID promotionId, boolean promotedOnly) {}

    record View(
        UUID id,
        UUID eventId,
        long ownerId,
        OrderStatus status,
        UUID promotionId,
        BigDecimal discountAmount,
        String discountCurrency,
        BigDecimal totalAmount,
        String totalCurrency,
        UUID paymentId,
        UUID refundId
    ) {}

    List<View> search(Filter filter, QueryPage page);
}
