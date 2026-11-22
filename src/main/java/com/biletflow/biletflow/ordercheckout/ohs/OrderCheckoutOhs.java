package com.biletflow.biletflow.ordercheckout.ohs;

import com.biletflow.biletflow.ordercheckout.ohs.dto.SupportOrderReference;
import java.util.Optional;
import java.util.UUID;

/** Published read contract for bounded contexts that need to validate an order reference. */
public interface OrderCheckoutOhs {
    Optional<SupportOrderReference> findSupportReference(UUID orderId);
}
