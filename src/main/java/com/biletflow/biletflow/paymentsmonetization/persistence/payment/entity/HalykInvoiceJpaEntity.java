package com.biletflow.biletflow.paymentsmonetization.persistence.payment.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pm_halyk_invoice_allocation")
public class HalykInvoiceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "halykInvoice")
    @SequenceGenerator(name = "halykInvoice", sequenceName = "pm_halyk_invoice_seq", allocationSize = 1)
    private Long id;

    @Column(name = "invoice_id", nullable = false, unique = true, length = 15)
    private String invoiceId;

    protected HalykInvoiceJpaEntity() {}

    public static HalykInvoiceJpaEntity allocate(String invoiceId) {
        var allocation = new HalykInvoiceJpaEntity();
        allocation.invoiceId = invoiceId;
        return allocation;
    }

    public Long getId() {
        return id;
    }
}
