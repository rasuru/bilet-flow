package com.biletflow.biletflow.ordercheckout.application.view;

import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import java.time.Instant;
import java.util.*;

public record CheckoutView(
    UUID id,
    UUID eventId,
    String status,
    java.math.BigDecimal amountKzt,
    Instant expiresAt,
    UUID paymentId,
    String refundState,
    UUID refundId,
    List<CheckoutInventoryOhs.IssuedTicket> tickets,
    boolean sandbox
) {}
