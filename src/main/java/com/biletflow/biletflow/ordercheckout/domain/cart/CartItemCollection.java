import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CartItemCollection {
    private final List<CartItem> items;

    public CartItemCollection(List<CartItem> items) {
        Objects.requireNonNull(items, "Cart items cannot be null");
        this.items = new ArrayList<>(items); // Defensive copy to prevent external modification
    }

    public static CartItemCollection empty() {
        return new CartItemCollection(new ArrayList<>());
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items); // Return an unmodifiable view to prevent external modification
    }

    public void addItem(CartItem item) {
        Objects.requireNonNull(item, "Cart item cannot be null");

        for (int i = 0; i < items.size(); i++) {
            CartItem existingItem = items.get(i);
            if (existingItem.ticketTypeId().equals(item.ticketTypeId())
                && existingItem.eventId().equals(item.eventId())) {
                // If the item already exists, update its quantity
                int newQuantity = existingItem.quantity() + item.quantity();
                items.set(i, existingItem.changeQuantity(newQuantity));
                return;
            }
        }
        
        items.add(item);
    }

    public void removeItem(UUID eventId, UUID ticketTypeId) {
        Objects.requireNonNull(eventId, "EventId cannot be null");
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
        items.removeIf(i -> i.eventId().equals(eventId) && i.ticketTypeId().equals(ticketTypeId));
    }

    public void changeItemQuantity(UUID eventId, UUID ticketTypeId, int newQuantity) {
        Objects.requireNonNull(eventId, "EventId cannot be null");
        Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");

        for (int i = 0; i < items.size(); i++) {
            CartItem existingItem = items.get(i);
            if (existingItem.eventId().equals(eventId) && existingItem.ticketTypeId().equals(ticketTypeId)) {
                
                items.set(i, existingItem.changeQuantity(newQuantity));
                return;
            }
        }
    }

    public void clear() {
        items.clear();
    }
}
