package com.biletflow.biletflow.ordercheckout.persistence.checkout;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.application.CheckoutProcess;
import com.biletflow.biletflow.ordercheckout.domain.checkout.*;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.order.*;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutHoldJpaEmbeddable;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutJpaEntity;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutLineJpaEmbeddable;
import com.biletflow.biletflow.ordercheckout.persistence.checkout.entity.CheckoutProcessJpaEntity;
import com.biletflow.biletflow.ordercheckout.persistence.common.entity.PurchaseItemJpaEmbeddable;
import com.biletflow.biletflow.ordercheckout.persistence.order.entity.OrderJpaEntity;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CheckoutSessionPersistenceMapper {

    public CheckoutJpaEntity toNewEntity(CheckoutSession domain) {
        return new CheckoutJpaEntity(
            domain.getId().value(),
            domain.getEventId(),
            domain.getOwnerId(),
            domain.getStatus(),
            domain.getInventoryMode(),
            domain.getPromotionId(),
            domain.getDiscountAmount().amount(),
            domain.getDiscountAmount().currency().getCurrencyCode(),
            domain.getTotalPrice().amount(),
            domain.getTotalPrice().currency().getCurrencyCode(),
            domain.getPaymentId(),
            domain
                .getItems()
                .stream()
                .map(item ->
                    new PurchaseItemJpaEmbeddable(
                        item.getTicketTypeId(),
                        item instanceof AssignedSeatingCheckoutItem assigned ? assigned.getSeatId() : null,
                        item.getPrice()
                    )
                )
                .toList()
        );
    }

    public void updateEntity(CheckoutSession domain, CheckoutJpaEntity entity) {
        if (!entity.getId().equals(domain.getId().value())) throw new IllegalArgumentException(
            "Cannot map onto another CheckoutSession id"
        );
        entity.updateMutableState(
            domain.getStatus(),
            domain.getPromotionId(),
            domain.getDiscountAmount().amount(),
            domain.getDiscountAmount().currency().getCurrencyCode(),
            domain.getTotalPrice().amount(),
            domain.getTotalPrice().currency().getCurrencyCode(),
            domain.getPaymentId(),
            domain
                .getItems()
                .stream()
                .map(item ->
                    new PurchaseItemJpaEmbeddable(
                        item.getTicketTypeId(),
                        item instanceof AssignedSeatingCheckoutItem assigned ? assigned.getSeatId() : null,
                        item.getPrice()
                    )
                )
                .toList()
        );
    }

    public CheckoutSession toDomain(CheckoutJpaEntity entity) {
        List<CheckoutItem> items = entity
            .getItems()
            .stream()
            .map(item ->
                item.getSeatId() == null
                    ? (CheckoutItem) new GeneralAdmissionCheckoutItem(item.getTicketTypeId(), item.getPrice())
                    : new AssignedSeatingCheckoutItem(item.getTicketTypeId(), item.getSeatId(), item.getPrice())
            )
            .toList();
        return new CheckoutSession(
            new CheckoutSessionId(entity.getId()),
            entity.getStatus(),
            entity.getOwnerId(),
            entity.getEventId(),
            entity.getMode(),
            new CheckoutItemCollection(items),
            entity.getPromotionId(),
            Money.of(entity.getDiscount(), Currency.getInstance(entity.getDiscountCurrency())),
            Money.of(entity.getTotal(), Currency.getInstance(entity.getTotalCurrency())),
            entity.getPaymentId()
        );
    }
}
