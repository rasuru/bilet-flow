package com.biletflow.biletflow.ticketinventory.external.paymentsmonetization;

import com.biletflow.biletflow.paymentsmonetization.ohs.PaymentsOhs;
import com.biletflow.biletflow.ticketinventory.application.tickettype.port.PaidSalesPort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaidSalesAcl implements PaidSalesPort {
    private final PaymentsOhs payments;

    public PaidSalesAcl(PaymentsOhs payments) {
        this.payments = Objects.requireNonNull(payments);
    }

    @Override
    public boolean permitsPaidSales(UUID eventId) {
        return payments.permitsPaidSales(eventId);
    }
}

