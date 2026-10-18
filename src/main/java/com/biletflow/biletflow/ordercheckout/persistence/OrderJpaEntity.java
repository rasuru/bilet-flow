package com.biletflow.biletflow.ordercheckout.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "oc_order")
public class OrderJpaEntity {

    @Id
    @Column(name = "id")
    UUID id;

    @Column(name = "event_id", nullable = false)
    UUID eventId;

    @Column(name = "owner_id", nullable = false)
    long ownerId;

    @Column(name = "status", nullable = false, length = 40)
    String status;

    @Column(name = "inventory_mode", nullable = false, length = 30)
    String mode;

    @Column(name = "promotion_id")
    UUID promotionId;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    BigDecimal discount;

    @Column(name = "discount_currency", nullable = false, length = 3)
    String discountCurrency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    BigDecimal total;

    @Column(name = "total_currency", nullable = false, length = 3)
    String totalCurrency;

    @Column(name = "payment_id")
    UUID paymentId;

    @Column(name = "cancellation_reason", length = 40)
    String reason;

    @Column(name = "refund_id")
    UUID refundId;

    @Version
    @Column(name = "version", nullable = false)
    long version;

    @ElementCollection
    @CollectionTable(name = "oc_order_item", joinColumns = @JoinColumn(name = "purchase_id"))
    @OrderColumn(name = "item_index")
    List<PurchaseItemJpaValue> items = new ArrayList<>();

    protected OrderJpaEntity() {}
}
