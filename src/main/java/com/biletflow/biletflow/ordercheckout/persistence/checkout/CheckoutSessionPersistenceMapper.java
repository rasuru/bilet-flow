package com.biletflow.biletflow.ordercheckout.persistence.checkout;

import com.biletflow.biletflow.common.domain;
import java.util.Currency;
import com.biletflow.biletflow.ordercheckout.domain.checkout.AssignedSeatingCheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutItemCollection;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutItemMode;
import com.biletflow.biletflow.ordercheckout.domain.checkout.GeneralAdmissionCheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSession;
import com.biletflow.biletflow.ordercheckout.domain.common.InventoryMode;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionId;
import com.biletflow.biletflow.ordercheckout.persistence.checkoutsession.entity.CheckoutSessionItemJpaEmbeddable;
import com.biletflow.biletflow.ordercheckout.persistence.checkoutsession.entity.CheckoutSessionJpaEntity;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CheckoutSessionPersistenceMapper {

    public CheckoutItemCollection toItems(CheckoutSessionJpaEntity entity) {
        CheckoutItemCollection items = new CheckoutItemCollection();

        for (CheckoutSessionItemJpaEmbeddable item : entity.getItems()) {
            CheckoutItemMode domainItem;

            if (item.getSeatId() != null) {
                domainItem = new AssignedSeatingCheckoutItem(
                    item.getTicketTypeId(),
                    item.getSeatId(),
                    item.getPrice()
                );
            } else {
                domainItem = new GeneralAdmissionCheckoutItem(
                    item.getTicketTypeId(),
                    item.getPrice()
                );
            }

            items.addItem(domainItem);
        }

        return items;
    }

    public CheckoutSession toDomain(CheckoutSessionJpaEntity entity) {
        return new CheckoutSession(
            new CheckoutSessionId(entity.getId()),
            entity.getStatus(),
            entity.getOwnerId(),
            entity.getEventId(),
            entity.getInventoryMode(),
            toItems(entity),
            entity.getPromotionId(),
            entity.getPromotionStatus(),
            new Money(entity.getDiscountAmount(), Currency.getInstance(entity.getPriceCurrency())),
            new Money(entity.getTotalPrice(), Currency.getInstance(entity.getPriceCurrency())),
            new Money(entity.getFinalPrice(), Currency.getInstance(entity.getPriceCurrency())),
            entity.getPaymentId()
        );
    }

    public CheckoutSessionJpaEntity toNewEntity(CheckoutSession domain) {
        return new CheckoutSessionJpaEntity(
            domain.getId().value(),
            domain.getStatus(),
            domain.getInventoryMode(),
            domain.getOwnerId(),
            domain.getEventId(),
            domain.getPromotionId(),
            domain.getPromotionStatus(),
            domain.getDiscountAmount().amount(),
            domain.getTotalPrice().amount(),
            domain.getFinalPrice().amount(),
            domain.getFinalPrice().currency().getCurrencyCode(),
            domain.getPaymentId(),
            toJpaItems(domain)
        );
    }

    private List<CheckoutSessionItemJpaEmbeddable> toJpaItems(
        CheckoutSession domain
    ) {
        return domain.getItems()
            .stream()
            .map(this::toJpaItem)
            .toList();
    }

    private CheckoutSessionItemJpaEmbeddable toJpaItem(
        CheckoutItemMode item
    ) {
        if (item instanceof AssignedSeatingCheckoutItem assigned) {
            return new CheckoutSessionItemJpaEmbeddable(
                assigned.getTicketTypeId(),
                assigned.getPrice(),
                assigned.getSeatId()
            );
        }

        if (item instanceof GeneralAdmissionCheckoutItem general) {
            return new CheckoutSessionItemJpaEmbeddable(
                general.getTicketTypeId(),
                general.getPrice(),
                null
            );
        }

        throw new IllegalArgumentException(
            "Unknown checkout item type: " + item.getClass()
        );
    }

    public void updateEntity(
        CheckoutSession domain,
        CheckoutSessionJpaEntity entity
    ) {
        // Only needed if you want to update an existing JPA entity.
    }
}
