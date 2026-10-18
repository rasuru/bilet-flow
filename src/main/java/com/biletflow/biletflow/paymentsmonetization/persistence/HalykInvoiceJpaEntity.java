package com.biletflow.biletflow.paymentsmonetization.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "pm_halyk_invoice_allocation")
public class HalykInvoiceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "halykInvoice")
    @SequenceGenerator(name = "halykInvoice", sequenceName = "pm_halyk_invoice_seq", allocationSize = 1)
    Long id;

    protected HalykInvoiceJpaEntity() {}
}
