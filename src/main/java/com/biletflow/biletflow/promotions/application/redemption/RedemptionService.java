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
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import java.time.Clock;
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
    private final Clock clock;

    public RedemptionService(CampaignRepository campaignRepository, PromoCodeRepository promoCodeRepository, Clock clock) {
        this.campaignRepository = Objects.requireNonNull(campaignRepository);
        this.promoCodeRepository = Objects.requireNonNull(promoCodeRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public PromotionQuoteView quote(QuotePromotionCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        PromoCode promoCode = requireRedeemableCode(command.code());
        Campaign campaign = requireCampaign(promoCode);
        campaign.ensureAppliesToEvent(command.eventId());

        DiscountQuote quote = campaign.quote(command.checkoutId(), toCheckoutLines(command.lines()), Instant.now(clock));

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

        PromoCode promoCode = requireRedeemableCode(command.code());
        Campaign campaign = requireCampaign(promoCode);
        campaign.ensureAppliesToEvent(command.eventId());

        PromoRedemption redemption = campaign.reserveRedemption(
            command.checkoutId(),
            toCheckoutLines(command.lines()),
            Instant.now(clock),
            command.expiresAt()
        );
        campaignRepository.save(campaign);

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

        RedemptionId redemptionId = toRedemptionId(command.redemptionId());
        Campaign campaign = requireCampaignOf(redemptionId);

        campaign.confirmRedemption(redemptionId, command.orderId(), Instant.now(clock));
        campaignRepository.save(campaign);
    }

    @Transactional
    public void release(ReleaseRedemptionCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        RedemptionId redemptionId = toRedemptionId(command.redemptionId());
        Campaign campaign = requireCampaignOf(redemptionId);

        campaign.releaseRedemption(redemptionId, command.checkoutId());
        campaignRepository.save(campaign);
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

    private Campaign requireCampaign(PromoCode promoCode) {
        return campaignRepository
            .findById(promoCode.getCampaignId())
            .orElseThrow(() -> new EntityNotFoundException("Campaign not found: " + promoCode.getCampaignId().value()));
    }

    private Campaign requireCampaignOf(RedemptionId redemptionId) {
        return campaignRepository
            .findByRedemptionId(redemptionId)
            .orElseThrow(() -> new EntityNotFoundException("Promo redemption not found: " + redemptionId.value()));
    }

    private static RedemptionId toRedemptionId(UUID redemptionId) {
        return new RedemptionId(Objects.requireNonNull(redemptionId, "redemptionId cannot be null"));
    }

    private static List<CheckoutLine> toCheckoutLines(List<PromotionLine> lines) {
        return lines
            .stream()
            .map(line -> new CheckoutLine(line.ticketTypeId(), line.unitPrice(), line.quantity()))
            .toList();
    }
}
