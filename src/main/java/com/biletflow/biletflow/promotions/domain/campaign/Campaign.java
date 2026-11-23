package com.biletflow.biletflow.promotions.domain.campaign;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import com.biletflow.biletflow.promotions.domain.exceptions.PromoCodeRejectedException;
import com.biletflow.biletflow.promotions.domain.exceptions.RejectionReason;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Campaign {

    private static final int MAX_NAME_LENGTH = 100;

    private final CampaignId id;
    private final UUID eventId;
    private final String name;
    private final Discount discount;
    private final ValidityPeriod validity;
    private final Set<UUID> applicableTicketTypeIds;
    private final RedemptionLimit redemptionLimit;
    private final Instant createdAt;

    private CampaignStatus status;
    private Instant endedAt;

    private Campaign(
        CampaignId id,
        UUID eventId,
        String name,
        Discount discount,
        ValidityPeriod validity,
        Set<UUID> applicableTicketTypeIds,
        RedemptionLimit redemptionLimit,
        CampaignStatus status,
        Instant createdAt,
        Instant endedAt
    ) {
        this.id = Objects.requireNonNull(id, "CampaignId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.name = validateName(name);
        this.discount = Objects.requireNonNull(discount, "discount cannot be null");
        this.validity = Objects.requireNonNull(validity, "validity cannot be null");
        this.applicableTicketTypeIds = Set.copyOf(
            Objects.requireNonNull(applicableTicketTypeIds, "applicableTicketTypeIds cannot be null")
        );
        this.redemptionLimit = Objects.requireNonNull(redemptionLimit, "redemptionLimit cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.endedAt = endedAt;

        if ((status == CampaignStatus.ENDED) != (endedAt != null)) {
            throw new IllegalStateException("endedAt must be set exactly when the campaign has ended");
        }
    }

    public static Campaign create(
        UUID eventId,
        String name,
        Discount discount,
        ValidityPeriod validity,
        Set<UUID> applicableTicketTypeIds,
        RedemptionLimit redemptionLimit,
        Instant now
    ) {
        Objects.requireNonNull(validity, "validity cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        if (validity.hasEndedAt(now)) {
            throw new IllegalArgumentException("Validity period must end in the future");
        }

        return new Campaign(
            CampaignId.generate(),
            eventId,
            name,
            discount,
            validity,
            applicableTicketTypeIds,
            redemptionLimit,
            CampaignStatus.ACTIVE,
            now,
            null
        );
    }

    public static Campaign rehydrate(
        CampaignId id,
        UUID eventId,
        String name,
        Discount discount,
        ValidityPeriod validity,
        Set<UUID> applicableTicketTypeIds,
        RedemptionLimit redemptionLimit,
        CampaignStatus status,
        Instant createdAt,
        Instant endedAt
    ) {
        return new Campaign(id, eventId, name, discount, validity, applicableTicketTypeIds, redemptionLimit, status, createdAt, endedAt);
    }

    public void ensureAcceptsRedemptions(int usedRedemptions, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (usedRedemptions < 0) {
            throw new IllegalArgumentException("Used redemptions cannot be negative");
        }
        if (status == CampaignStatus.ENDED) {
            throw new PromoCodeRejectedException(RejectionReason.CAMPAIGN_ENDED);
        }
        if (validity.hasNotStartedAt(now)) {
            throw new PromoCodeRejectedException(RejectionReason.NOT_YET_VALID);
        }
        if (validity.hasEndedAt(now)) {
            throw new PromoCodeRejectedException(RejectionReason.EXPIRED);
        }
        if (redemptionLimit.isReachedBy(usedRedemptions)) {
            throw new PromoCodeRejectedException(RejectionReason.EXHAUSTED);
        }
    }

    public DiscountQuote quote(List<CheckoutLine> lines, int usedRedemptions, Instant now) {
        ensureAcceptsRedemptions(usedRedemptions, now);

        List<CheckoutLine> eligibleLines = eligibleLines(lines);
        Money eligibleSubtotal = eligibleLines.stream().map(CheckoutLine::total).reduce(Money.zeroKzt(), Money::add);

        if (eligibleSubtotal.isZero()) {
            throw new PromoCodeRejectedException(RejectionReason.INAPPLICABLE);
        }

        int eligibleTicketQuantity = eligibleLines.stream().mapToInt(CheckoutLine::quantity).sum();

        return new DiscountQuote(eligibleSubtotal, eligibleTicketQuantity, discount.calculateFor(eligibleSubtotal));
    }

    public void end(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (status == CampaignStatus.ENDED) {
            throw new InvalidCampaignStateException("Campaign has already ended");
        }

        status = CampaignStatus.ENDED;
        endedAt = now;
    }

    public void ensureAppliesToEvent(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");

        if (!this.eventId.equals(eventId)) {
            throw new PromoCodeRejectedException(RejectionReason.INAPPLICABLE);
        }
    }

    public boolean appliesTo(UUID ticketTypeId) {
        return applicableTicketTypeIds.isEmpty() || applicableTicketTypeIds.contains(ticketTypeId);
    }

    public Integer remainingRedemptions(int usedRedemptions) {
        if (redemptionLimit.isUnlimited()) {
            return null;
        }
        return Math.max(0, redemptionLimit.maxRedemptions() - usedRedemptions);
    }

    private List<CheckoutLine> eligibleLines(List<CheckoutLine> lines) {
        Objects.requireNonNull(lines, "lines cannot be null");

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Checkout must contain at least one line");
        }

        return lines
            .stream()
            .map(line -> Objects.requireNonNull(line, "line cannot be null"))
            .filter(line -> appliesTo(line.ticketTypeId()))
            .toList();
    }

    private static String validateName(String name) {
        Objects.requireNonNull(name, "name cannot be null");

        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Campaign name cannot be blank");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Campaign name cannot be longer than " + MAX_NAME_LENGTH + " characters");
        }

        return trimmed;
    }

    public CampaignId getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public Discount getDiscount() {
        return discount;
    }

    public ValidityPeriod getValidity() {
        return validity;
    }

    public Set<UUID> getApplicableTicketTypeIds() {
        return applicableTicketTypeIds;
    }

    public RedemptionLimit getRedemptionLimit() {
        return redemptionLimit;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
