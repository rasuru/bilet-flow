package com.biletflow.biletflow.iam.persistence.organizer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "iam_organizer_profile")
public class OrganizerProfileJpaEntity {

    @Id
    @Column(name = "organizer_id", nullable = false, updatable = false)
    private Long organizerId;

    @Column(name = "organization_name", nullable = false, length = 120)
    private String organizationName;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "website", length = 512)
    private String website;

    @Column(name = "address", length = 512)
    private String address;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizerProfileJpaEntity() {}

    public OrganizerProfileJpaEntity(Long organizerId, String organizationName, String contactName, String contactEmail,
                                     String contactPhone, String website, String address, String description, Instant updatedAt) {
        this.organizerId = organizerId;
        update(organizationName, contactName, contactEmail, contactPhone, website, address, description, updatedAt);
    }

    public void update(String organizationName, String contactName, String contactEmail, String contactPhone,
                       String website, String address, String description, Instant updatedAt) {
        this.organizationName = organizationName;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.website = website;
        this.address = address;
        this.description = description;
        this.updatedAt = updatedAt;
    }

    public Long getOrganizerId() { return organizerId; }
    public String getOrganizationName() { return organizationName; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getWebsite() { return website; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }
}
