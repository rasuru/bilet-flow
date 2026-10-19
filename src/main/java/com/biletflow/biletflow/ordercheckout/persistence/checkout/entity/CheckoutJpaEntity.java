package com.biletflow.biletflow.ordercheckout.persistence.checkout.entity;

import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionStatus;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.persistence.common.entity.PurchaseItemJpaEmbeddable;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "oc_checkout")
public class CheckoutJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "owner_id", nullable = false)
    private long ownerId;

    @Column(name = "status", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private CheckoutSessionStatus status;

    @Column(name = "inventory_mode", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private InventoryMode mode;

    @Column(name = "promotion_id")
    private UUID promotionId;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discount;

    @Column(name = "discount_currency", nullable = false, length = 3)
    private String discountCurrency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "total_currency", nullable = false, length = 3)
    private String totalCurrency;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ElementCollection
    @CollectionTable(name = "oc_checkout_item", joinColumns = @JoinColumn(name = "purchase_id"))
    @OrderColumn(name = "item_index")
    private List<PurchaseItemJpaEmbeddable> items = new ArrayList<>();

    protected CheckoutJpaEntity() {}

    public CheckoutJpaEntity(
        UUID id,
        UUID eventId,
        long ownerId,
        CheckoutSessionStatus status,
        InventoryMode mode,
        UUID promotionId,
        BigDecimal discount,
        String discountCurrency,
        BigDecimal total,
        String totalCurrency,
        UUID paymentId,
        List<PurchaseItemJpaEmbeddable> items
    ) {
        this.id = id;
        this.eventId = eventId;
        this.ownerId = ownerId;
        this.status = status;
        this.mode = mode;
        this.promotionId = promotionId;
        this.discount = discount;
        this.discountCurrency = discountCurrency;
        this.total = total;
        this.totalCurrency = totalCurrency;
        this.paymentId = paymentId;
        this.items.addAll(items);
    }

    public void updateMutableState(
        CheckoutSessionStatus status,
        UUID promotionId,
        BigDecimal discount,
        String discountCurrency,
        BigDecimal total,
        String totalCurrency,
        UUID paymentId,
        List<PurchaseItemJpaEmbeddable> items
    ) {
        this.status = status;
        this.promotionId = promotionId;
        this.discount = discount;
        this.discountCurrency = discountCurrency;
        this.total = total;
        this.totalCurrency = totalCurrency;
        this.paymentId = paymentId;
        this.items.clear();
        this.items.addAll(items);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public CheckoutSessionStatus getStatus() {
        return status;
    }

    public InventoryMode getMode() {
        return mode;
    }

    public UUID getPromotionId() {
        return promotionId;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public String getDiscountCurrency() {
        return discountCurrency;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getTotalCurrency() {
        return totalCurrency;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public long getVersion() {
        return version;
    }

    public List<PurchaseItemJpaEmbeddable> getItems() {
        return List.copyOf(items);
    }
}
