import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CartItem(UUID eventId, UUID ticketTypeId, int quantity, Instant addedAt) {
        public CartItem {
            Objects.requireNonNull(eventId, "EventId cannot be null");
            Objects.requireNonNull(ticketTypeId, "TicketTypeId cannot be null");
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }
            Objects.requireNonNull(addedAt, "AddedAt cannot be null");
        }
    }

    public CartItem changeQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        return new CartItem(eventId, ticketTypeId, newQuantity, addedAt);
    }
}
