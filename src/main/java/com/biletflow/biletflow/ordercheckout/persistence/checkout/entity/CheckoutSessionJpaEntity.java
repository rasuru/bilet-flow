package com.biletflow.biletflow.ordercheckout.persistence.checkoutsession.entity;

import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.checkout.enums.PromotionStatus;
import com.biletflow.biletflow.ordercheckout.domain.checkout.enums.CheckoutSessionStatus;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "checkout_session",
    indexes = {
        @Index(name = "idx_checkout_session_order", columnList = "order_id"),
        @Index(name = "idx_checkout_session_event", columnList = "event_id"),
        @Index(name = "idx_checkout_session_owner", columnList = "owner_user_id")
    }
)
public class CheckoutSessionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CheckoutSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_mode", nullable = false, length = 32)
    private InventoryMode inventoryMode;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "promotion_id", updatable = false)
    private UUID promotionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_status", nullable = false, length = 32)
    private PromotionStatus promotionStatus;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "final_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "checkout_session_item",
        joinColumns = @JoinColumn(name = "checkout_session_id")
    )
    private List<CheckoutSessionItemJpaEmbeddable> checkoutSessionItems =
        new ArrayList<>();

    protected CheckoutSessionJpaEntity() {
    }

    public CheckoutSessionJpaEntity(
        UUID id,
        CheckoutSessionStatus status,
        InventoryMode inventoryMode,
        Long ownerId,
        UUID eventId,
        UUID promotionId,
        PromotionStatus promotionStatus,
        BigDecimal discountAmount,
        BigDecimal totalPrice,
        BigDecimal finalPrice,
        String priceCurrency,
        UUID paymentId,
        List<CheckoutSessionItemJpaEmbeddable> checkoutSessionItems
    ) {
        this.id = id;
        this.status = status;
        this.inventoryMode = inventoryMode;
        this.ownerId = ownerId;
        this.eventId = eventId;
        this.promotionId = promotionId;
        this.promotionStatus = promotionStatus;
        this.discountAmount = discountAmount;
        this.totalPrice = totalPrice;
        this.finalPrice = finalPrice;
        this.priceCurrency = priceCurrency;
        this.paymentId = paymentId;
        this.checkoutSessionItems = checkoutSessionItems;
    }

    public UUID getId() {
        return id;
    }

    public CheckoutSessionStatus getStatus() {
        return status;
    }

    public InventoryMode getInventoryMode() {
        return inventoryMode;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public PromotionStatus getPromotionStatus() {
        return promotionStatus;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public BigDecimal getFinalPrice() {
        return finalPrice;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public List<CheckoutSessionItemJpaEmbeddable> getItems() {
        return checkoutSessionItems;
    }
}
