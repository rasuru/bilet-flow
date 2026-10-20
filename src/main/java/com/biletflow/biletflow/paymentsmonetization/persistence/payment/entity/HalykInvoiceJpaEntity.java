package com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pm_halyk_invoice_allocation")
public class HalykInvoiceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "halykInvoice")
    @SequenceGenerator(name = "halykInvoice", sequenceName = "pm_halyk_invoice_seq", allocationSize = 1)
    private Long id;

    protected HalykInvoiceJpaEntity() {}

    public static HalykInvoiceJpaEntity allocate() {
        return new HalykInvoiceJpaEntity();
    }

    public Long getId() {
        return id;
    }
}
