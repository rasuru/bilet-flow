package com.biletflow.biletflow.ordercheckout.domain.order;

public class Order {
    private final OrderId id;

    private final long ownerId;
    private final UUID eventId;

    private final InventoryMode inventoryMode;
    private final OrderItemCollection items;

    private final UUID promotionId;
    private final double discountAmount;

    private final double totalPrice;  // Total price before discount
    private final double finalPrice;  // Final price after discount

    private final UUID paymentId;
}
