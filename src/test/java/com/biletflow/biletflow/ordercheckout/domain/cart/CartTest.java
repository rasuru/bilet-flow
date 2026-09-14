package com.biletflow.biletflow.ordercheckout.domain.cart;

import org.junit.jupiter.api.Test;
import com.biletflow.biletflow.ordercheckout.domain.cart.Cart;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    @Test
    void createNew_shouldCreateEmptyCart() {
        long userId = 123L;

        Cart cart = Cart.createNew(userId);

        assertNotNull(cart.getId());
        assertEquals(userId, cart.getUserId());
        assertNotNull(cart.getCreatedAt());
        assertNotNull(cart.getItems());
        assertTrue(cart.getItems().getItems().isEmpty());
    }

    @Test
    void addItem_shouldAddItemToCart() {
        Cart cart = Cart.createNew(123L);

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        cart.addItem(eventId, ticketTypeId, 2);

        assertEquals(1, cart.getItems().getItems().size());

        CartItem item = cart.getItems().getItems().get(0);

        assertEquals(eventId, item.eventId());
        assertEquals(ticketTypeId, item.ticketTypeId());
        assertEquals(2, item.quantity());
    }

    @Test
    void addItem_shouldIncreaseQuantity_whenSameItemAlreadyExists() {
        Cart cart = Cart.createNew(123L);

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        cart.addItem(eventId, ticketTypeId, 2);
        cart.addItem(eventId, ticketTypeId, 3);

        assertEquals(1, cart.getItems().getItems().size());

        CartItem item = cart.getItems().getItems().get(0);

        assertEquals(5, item.quantity());
    }

    @Test
    void addItem_shouldCreateSeparateItems_forDifferentTicketTypes() {
        Cart cart = Cart.createNew(123L);

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeA = UUID.randomUUID();
        UUID ticketTypeB = UUID.randomUUID();

        cart.addItem(eventId, ticketTypeA, 2);
        cart.addItem(eventId, ticketTypeB, 3);

        assertEquals(2, cart.getItems().getItems().size());
    }

    @Test
    void addItem_shouldCreateSeparateItems_forDifferentEvents() {
        Cart cart = Cart.createNew(123L);

        UUID eventA = UUID.randomUUID();
        UUID eventB = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        cart.addItem(eventA, ticketTypeId, 2);
        cart.addItem(eventB, ticketTypeId, 3);

        assertEquals(2, cart.getItems().getItems().size());
    }

    @Test
    void removeItem_shouldRemoveMatchingItem() {
        Cart cart = Cart.createNew(123L);

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        cart.addItem(eventId, ticketTypeId, 2);

        cart.removeItem(eventId, ticketTypeId);

        assertTrue(cart.getItems().getItems().isEmpty());
    }

    @Test
    void removeItem_shouldNotRemoveDifferentItem() {
        Cart cart = Cart.createNew(123L);

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeA = UUID.randomUUID();
        UUID ticketTypeB = UUID.randomUUID();

        cart.addItem(eventId, ticketTypeA, 2);

        cart.removeItem(eventId, ticketTypeB);

        assertEquals(1, cart.getItems().getItems().size());
        assertEquals(2, cart.getItems().getItems().get(0).quantity());
    }

    @Test
    void changeItemQuantity_shouldChangeQuantity() {
        Cart cart = Cart.createNew(123L);

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        cart.addItem(eventId, ticketTypeId, 2);

        cart.changeItemQuantity(eventId, ticketTypeId, 5);

        assertEquals(1, cart.getItems().getItems().size());
        assertEquals(5, cart.getItems().getItems().get(0).quantity());
    }

    @Test
    void changeItemQuantity_shouldNotAffectDifferentItem() {
        Cart cart = Cart.createNew(123L);

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeA = UUID.randomUUID();
        UUID ticketTypeB = UUID.randomUUID();

        cart.addItem(eventId, ticketTypeA, 2);
        cart.addItem(eventId, ticketTypeB, 3);

        cart.changeItemQuantity(eventId, ticketTypeA, 5);

        assertEquals(5,
                cart.getItems().getItems().stream()
                        .filter(i -> i.ticketTypeId().equals(ticketTypeA))
                        .findFirst()
                        .orElseThrow()
                        .quantity());

        assertEquals(3,
                cart.getItems().getItems().stream()
                        .filter(i -> i.ticketTypeId().equals(ticketTypeB))
                        .findFirst()
                        .orElseThrow()
                        .quantity());
    }

    @Test
    void clearItems_shouldRemoveAllItems() {
        Cart cart = Cart.createNew(123L);

        cart.addItem(UUID.randomUUID(), UUID.randomUUID(), 2);
        cart.addItem(UUID.randomUUID(), UUID.randomUUID(), 3);

        cart.clearItems();

        assertTrue(cart.getItems().getItems().isEmpty());
    }

    @Test
    void addItem_shouldRejectZeroQuantity() {
        Cart cart = Cart.createNew(123L);

        assertThrows(
                IllegalArgumentException.class,
                () -> cart.addItem(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        0
                )
        );
    }

    @Test
    void addItem_shouldRejectNegativeQuantity() {
        Cart cart = Cart.createNew(123L);

        assertThrows(
                IllegalArgumentException.class,
                () -> cart.addItem(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        -1
                )
        );
    }

    @Test
    void changeItemQuantity_shouldRejectZeroQuantity() {
        Cart cart = Cart.createNew(123L);

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        cart.addItem(eventId, ticketTypeId, 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> cart.changeItemQuantity(
                        eventId,
                        ticketTypeId,
                        0
                )
        );
    }

    @Test
    void changeItemQuantity_shouldRejectNegativeQuantity() {
        Cart cart = Cart.createNew(123L);

        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        cart.addItem(eventId, ticketTypeId, 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> cart.changeItemQuantity(
                        eventId,
                        ticketTypeId,
                        -1
                )
        );
    }

    @Test
    void removeItem_shouldRejectNullEventId() {
        Cart cart = Cart.createNew(123L);

        assertThrows(
                NullPointerException.class,
                () -> cart.removeItem(null, UUID.randomUUID())
        );
    }

    @Test
    void removeItem_shouldRejectNullTicketTypeId() {
        Cart cart = Cart.createNew(123L);

        assertThrows(
                NullPointerException.class,
                () -> cart.removeItem(UUID.randomUUID(), null)
        );
    }

    @Test
    void changeItemQuantity_shouldRejectNullEventId() {
        Cart cart = Cart.createNew(123L);

        assertThrows(
                NullPointerException.class,
                () -> cart.changeItemQuantity(null, UUID.randomUUID(), 2)
        );
    }

    @Test
    void changeItemQuantity_shouldRejectNullTicketTypeId() {
        Cart cart = Cart.createNew(123L);

        assertThrows(
                NullPointerException.class,
                () -> cart.changeItemQuantity(UUID.randomUUID(), null, 2)
        );
    }
    
  @Test
  void changeItemQuantity_shouldThrow_whenItemDoesNotExist() {
      Cart cart = Cart.createNew(123L);

      assertThrows(
              IllegalArgumentException.class,
              () -> cart.changeItemQuantity(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      5
              )
      );
  }
}
