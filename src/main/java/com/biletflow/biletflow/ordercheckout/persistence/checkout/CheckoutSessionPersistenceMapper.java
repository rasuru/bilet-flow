package com.biletflow.biletflow.ordercheckout.persistence.checkout;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.ordercheckout.domain.checkout.AssignedSeatingCheckoutItem;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutItemCollection;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutItemMode;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSession;
import com.biletflow.biletflow.ordercheckout.domain.checkout.CheckoutSessionId;
import com.biletflow.biletflow.ordercheckout.domain.checkout.GeneralAdmissionCheckoutItem;
import com.biletflow.biletflow.ordercheckout.persistence.checkoutsession.entity.CheckoutSessionItemJpaEmbeddable;
import com.biletflow.biletflow.ordercheckout.persistence.checkoutsession.entity.CheckoutSessionJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.List;

@Component
public class CheckoutSessionPersistenceMapper {

    public CheckoutItemCollection toItems(CheckoutSessionJpaEntity entity) {
        CheckoutItemCollection items = new CheckoutItemCollection();

        for (CheckoutSessionItemJpaEmbeddable item : entity.getItems()) {
            CheckoutItemMode domainItem;

            Money price = new Money(
                item.getPrice(),
                Currency.getInstance(item.getPriceCurrency())
            );

            if (item.getSeatId() != null) {
                domainItem = new AssignedSeatingCheckoutItem(
                    item.getTicketTypeId(),
                    item.getSeatId(),
                    price
                );
            } else {
                domainItem = new GeneralAdmissionCheckoutItem(
                    item.getTicketTypeId(),
                    price
                );
            }

            items.addItem(domainItem);
        }

        return items;
    }

    public CheckoutSession toDomain(CheckoutSessionJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getPriceCurrency());

        return new CheckoutSession(
            new CheckoutSessionId(entity.getId()),
            entity.getStatus(),
            entity.getOwnerId(),
            entity.getEventId(),
            entity.getInventoryMode(),
            toItems(entity),
            entity.getPromotionId(),
            entity.getPromotionStatus(),
            new Money(entity.getDiscountAmount(), currency),
            new Money(entity.getTotalPrice(), currency),
            new Money(entity.getFinalPrice(), currency),
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
                assigned.getPrice().amount(),
                assigned.getPrice().currency().getCurrencyCode(),
                assigned.getSeatId()
            );
        }

        if (item instanceof GeneralAdmissionCheckoutItem general) {
            return new CheckoutSessionItemJpaEmbeddable(
                general.getTicketTypeId(),
                general.getPrice().amount(),
                general.getPrice().currency().getCurrencyCode(),
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
        // TODO
    }
}
