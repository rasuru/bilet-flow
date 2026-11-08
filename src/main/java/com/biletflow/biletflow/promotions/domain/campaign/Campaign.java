package com.biletflow.biletflow.promotions.domain.campaign;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import com.biletflow.biletflow.promotions.domain.exceptions.PromoCodeRejectedException;
import com.biletflow.biletflow.promotions.domain.exceptions.RedemptionNotFoundException;
import com.biletflow.biletflow.promotions.domain.exceptions.RejectionReason;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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

    private final List<PromoRedemption> redemptions;

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
        Instant endedAt,
        List<PromoRedemption> redemptions
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
        this.redemptions = new ArrayList<>(Objects.requireNonNull(redemptions, "redemptions cannot be null"));

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
            null,
            List.of()
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
        Instant endedAt,
        List<PromoRedemption> redemptions
    ) {
        return new Campaign(
            id,
            eventId,
            name,
            discount,
            validity,
            applicableTicketTypeIds,
            redemptionLimit,
            status,
            createdAt,
            endedAt,
            redemptions
        );
    }

    public DiscountQuote quote(UUID sessionId, List<CheckoutLine> lines, Instant now) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        ensureRedeemableAt(now);

        if (redemptionLimit.isReachedBy(usedRedemptionsExcluding(sessionId, now))) {
            throw new PromoCodeRejectedException(RejectionReason.EXHAUSTED);
        }

        Money eligibleSubtotal = eligibleSubtotal(lines);
        if (eligibleSubtotal.isZero()) {
            throw new PromoCodeRejectedException(RejectionReason.INAPPLICABLE);
        }

        return new DiscountQuote(eligibleSubtotal, discount.calculateFor(eligibleSubtotal));
    }

    public PromoRedemption reserveRedemption(UUID sessionId, List<CheckoutLine> lines, Instant now, Instant expiresAt) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(now, "now cannot be null");

        expireStaleRedemptions(now);

        DiscountQuote quote = quote(sessionId, lines, now);

        redemptions
            .stream()
            .filter(redemption -> redemption.isActiveReservationFor(sessionId, now))
            .forEach(redemption -> redemption.release(sessionId));

        PromoRedemption redemption = PromoRedemption.reserve(sessionId, quote.discountAmount(), now, expiresAt);
        redemptions.add(redemption);
        return redemption;
    }

    public void confirmRedemption(RedemptionId redemptionId, UUID orderId, Instant now) {
        findRedemption(redemptionId).confirm(orderId, now);
    }

    public void releaseRedemption(RedemptionId redemptionId, UUID sessionId) {
        findRedemption(redemptionId).release(sessionId);
    }

    public void expireStaleRedemptions(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        for (PromoRedemption redemption : redemptions) {
            if (redemption.getStatus() == RedemptionStatus.RESERVED && redemption.isExpiredAt(now)) {
                redemption.markExpired();
            }
        }
    }

    public void end(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        if (status == CampaignStatus.ENDED) {
            throw new InvalidCampaignStateException("Campaign has already ended");
        }

        status = CampaignStatus.ENDED;
        endedAt = now;
    }

    public PromoRedemption findRedemption(RedemptionId redemptionId) {
        Objects.requireNonNull(redemptionId, "RedemptionId cannot be null");

        return redemptions
            .stream()
            .filter(redemption -> redemption.getId().equals(redemptionId))
            .findFirst()
            .orElseThrow(() -> new RedemptionNotFoundException(redemptionId.value().toString()));
    }

    public boolean appliesTo(UUID ticketTypeId) {
        return applicableTicketTypeIds.isEmpty() || applicableTicketTypeIds.contains(ticketTypeId);
    }

    public int usedRedemptionsAt(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        return (int) redemptions.stream().filter(redemption -> redemption.countsTowardsLimitAt(now)).count();
    }

    public Integer remainingRedemptionsAt(Instant now) {
        if (redemptionLimit.isUnlimited()) {
            return null;
        }
        return Math.max(0, redemptionLimit.maxRedemptions() - usedRedemptionsAt(now));
    }

    public int confirmedRedemptionCount() {
        return (int) redemptions.stream().filter(redemption -> redemption.getStatus() == RedemptionStatus.CONFIRMED).count();
    }

    private void ensureRedeemableAt(Instant now) {
        if (status == CampaignStatus.ENDED) {
            throw new PromoCodeRejectedException(RejectionReason.CAMPAIGN_ENDED);
        }
        if (validity.hasNotStartedAt(now)) {
            throw new PromoCodeRejectedException(RejectionReason.NOT_YET_VALID);
        }
        if (validity.hasEndedAt(now)) {
            throw new PromoCodeRejectedException(RejectionReason.EXPIRED);
        }
    }

    private int usedRedemptionsExcluding(UUID sessionId, Instant now) {
        return (int) redemptions
            .stream()
            .filter(redemption -> redemption.countsTowardsLimitAt(now))
            .filter(redemption -> !redemption.isActiveReservationFor(sessionId, now))
            .count();
    }

    private Money eligibleSubtotal(List<CheckoutLine> lines) {
        Objects.requireNonNull(lines, "lines cannot be null");

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Checkout must contain at least one line");
        }

        return lines
            .stream()
            .map(line -> Objects.requireNonNull(line, "line cannot be null"))
            .filter(line -> appliesTo(line.ticketTypeId()))
            .map(CheckoutLine::total)
            .reduce(Money.zeroKzt(), Money::add);
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

    public List<PromoRedemption> getRedemptions() {
        return Collections.unmodifiableList(redemptions);
    }
}
