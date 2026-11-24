package com.biletflow.biletflow.promotions.application.redemption;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.promotions.application.redemption.command.ConfirmRedemptionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.PromotionLine;
import com.biletflow.biletflow.promotions.application.redemption.command.QuotePromotionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.ReleaseRedemptionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.ReservePromotionCommand;
import com.biletflow.biletflow.promotions.application.redemption.view.PromotionQuoteView;
import com.biletflow.biletflow.promotions.application.redemption.view.ReservedPromotionView;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignRepository;
import com.biletflow.biletflow.promotions.domain.campaign.CheckoutLine;
import com.biletflow.biletflow.promotions.domain.campaign.DiscountQuote;
import com.biletflow.biletflow.promotions.domain.promocode.CodeValue;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeRepository;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemptionRepository;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import com.biletflow.biletflow.promotions.domain.redemption.ReservationLifetime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RedemptionService {

    private final CampaignRepository campaignRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository redemptionRepository;
    private final ReservationLifetime reservationLifetime;
    private final Clock clock;

    public RedemptionService(
        CampaignRepository campaignRepository,
        PromoCodeRepository promoCodeRepository,
        PromoRedemptionRepository redemptionRepository,
        ReservationLifetime reservationLifetime,
        Clock clock
    ) {
        this.campaignRepository = Objects.requireNonNull(campaignRepository);
        this.promoCodeRepository = Objects.requireNonNull(promoCodeRepository);
        this.redemptionRepository = Objects.requireNonNull(redemptionRepository);
        this.reservationLifetime = Objects.requireNonNull(reservationLifetime);
        this.clock = Objects.requireNonNull(clock);
    }

    public PromotionQuoteView quote(QuotePromotionCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(command.checkoutId(), "checkoutId cannot be null");

        Instant now = Instant.now(clock);
        PromoCode promoCode = requireRedeemableCode(command.code());
        Campaign campaign = campaignRepository.findById(promoCode.getCampaignId()).orElseThrow(() -> campaignNotFound(promoCode));
        campaign.ensureAppliesToEvent(command.eventId());

        int ownReservations = redemptionRepository.findActiveReservations(campaign.getId(), command.checkoutId(), now).size();
        int usedByOthers = redemptionRepository.countActive(campaign.getId(), now) - ownReservations;
        DiscountQuote quote = campaign.quote(toCheckoutLines(command.lines()), usedByOthers, now);

        return new PromotionQuoteView(
            campaign.getId().value(),
            promoCode.getCode().value(),
            quote.eligibleSubtotal(),
            quote.discountAmount()
        );
    }

    @Transactional
    public ReservedPromotionView reserve(ReservePromotionCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(command.checkoutId(), "checkoutId cannot be null");

        PromoCode promoCode = requireRedeemableCode(command.code());
        Campaign campaign = campaignRepository.findByIdForUpdate(promoCode.getCampaignId()).orElseThrow(() -> campaignNotFound(promoCode));
        campaign.ensureAppliesToEvent(command.eventId());
        Instant now = Instant.now(clock);

        if (command.checkoutDeadline() != null && !now.isBefore(command.checkoutDeadline())) {
            throw new IllegalStateException("Checkout inventory hold has expired");
        }
        var lifetime = command.checkoutDeadline() == null
            ? reservationLifetime
            : new ReservationLifetime(Duration.between(now, command.checkoutDeadline()));
        for (PromoRedemption previous : redemptionRepository.findActiveReservations(campaign.getId(), command.checkoutId(), now)) {
            previous.release(command.checkoutId());
            redemptionRepository.save(previous);
        }

        int used = redemptionRepository.countActive(campaign.getId(), now);
        DiscountQuote quote = campaign.quote(toCheckoutLines(command.lines()), used, now);

        PromoRedemption redemption = redemptionRepository.save(
            PromoRedemption.reserve(
                campaign.getId(),
                promoCode.getId(),
                command.checkoutId(),
                command.source(),
                quote,
                now,
                lifetime
            )
        );

        return new ReservedPromotionView(
            redemption.getId().value(),
            campaign.getId().value(),
            promoCode.getCode().value(),
            redemption.getDiscountAmount(),
            redemption.getExpiresAt()
        );
    }

    @Transactional
    public void confirm(ConfirmRedemptionCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        PromoRedemption redemption = requireRedemption(command.redemptionId());
        Instant acceptedAt = command.acceptedAt() == null ? Instant.now(clock) : command.acceptedAt();
        if (redemption.confirm(command.orderId(), acceptedAt)) {
            redemptionRepository.save(redemption);
        }
    }

    @Transactional
    public void release(ReleaseRedemptionCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        PromoRedemption redemption = requireRedemption(command.redemptionId());
        redemption.release(command.checkoutId());
        redemptionRepository.save(redemption);
    }

    private PromoCode requireRedeemableCode(String rawCode) {
        Objects.requireNonNull(rawCode, "code cannot be null");

        CodeValue code;
        try {
            code = new CodeValue(rawCode);
        } catch (IllegalArgumentException exception) {
            throw new EntityNotFoundException("Promo code not found");
        }

        PromoCode promoCode = promoCodeRepository.findByCode(code).orElseThrow(() -> new EntityNotFoundException("Promo code not found"));
        promoCode.ensureRedeemable();
        return promoCode;
    }

    private PromoRedemption requireRedemption(UUID redemptionId) {
        Objects.requireNonNull(redemptionId, "redemptionId cannot be null");

        return redemptionRepository
            .findById(new RedemptionId(redemptionId))
            .orElseThrow(() -> new EntityNotFoundException("Promo redemption not found: " + redemptionId));
    }

    private static EntityNotFoundException campaignNotFound(PromoCode promoCode) {
        return new EntityNotFoundException("Campaign not found: " + promoCode.getCampaignId().value());
    }

    private static List<CheckoutLine> toCheckoutLines(List<PromotionLine> lines) {
        return lines
            .stream()
            .map(line -> new CheckoutLine(line.ticketTypeId(), line.unitPrice(), line.quantity()))
            .toList();
    }
}
