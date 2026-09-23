package com.biletflow.biletflow.ordercheckout.persistence.order.entity;

import com.biletflow.biletflow.ordercheckout.domain.order.enums.CancellationReason;
import com.biletflow.biletflow.ordercheckout.domain.order.enums.OrderStatus;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_order_owner", columnList = "owner_user_id"),
        @Index(name = "idx_order_event", columnList = "event_id")
    }
)
public class OrderJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_start_time")
    private Instant eventStartTime;

    @Column(name = "event_end_time")
    private Instant eventEndTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_mode", nullable = false, length = 32)
    private InventoryMode inventoryMode;

    @Column(name = "promotion_id", updatable = false)
    private UUID promotionId;

    @Column(name = "discount_amount", nullable = false, updatable = false)
    private double discountAmount;

    @Column(name = "total_price", nullable = false, updatable = false)
    private double totalPrice;

    @Column(name = "final_price", nullable = false, updatable = false)
    private double finalPrice;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 32)
    private CancellationReason cancellationReason;

    @Column(name = "refund_id")
    private UUID refundId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "order_item",
        joinColumns = @JoinColumn(name = "order_id")
    )
    private List<OrderItemJpaEmbeddable> items = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    public OrderJpaEntity(
        UUID id,
        OrderStatus status,
        Long ownerId,
        UUID eventId,
        Instant eventStartTime,
        Instant eventEndTime,
        InventoryMode inventoryMode,
        UUID promotionId,
        double discountAmount,
        double totalPrice,
        double finalPrice,
        UUID paymentId,
        CancellationReason cancellationReason,
        UUID refundId,
        List<OrderItemJpaEmbeddable> items
    ) {
        this.id = id;
        this.status = status;
        this.ownerId = ownerId;
        this.eventId = eventId;
        this.eventStartTime = eventStartTime;
        this.eventEndTime = eventEndTime;
        this.inventoryMode = inventoryMode;
        this.promotionId = promotionId;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.finalPrice = finalPrice;
        this.paymentId = paymentId;
        this.cancellationReason = cancellationReason;
        this.refundId = refundId;
        this.items = items;
    }

    public UUID getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getEventStartTime() {
        return eventStartTime;
    }

    public Instant getEventEndTime() {
        return eventEndTime;
    }

    public InventoryMode getInventoryMode() {
        return inventoryMode;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public double getDiscountAmount() {
        return discountAmount;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public double getFinalPrice() {
        return finalPrice;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public List<OrderItemJpaEmbeddable> getItems() {
        return items;
    }
}
