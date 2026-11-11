package com.biletflow.biletflow.promotions.application.promocode;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditType;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditService;
import com.biletflow.biletflow.promotions.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.promotions.application.promocode.command.DisablePromoCodeCommand;
import com.biletflow.biletflow.promotions.application.promocode.command.EnablePromoCodeCommand;
import com.biletflow.biletflow.promotions.application.promocode.view.CampaignLinkView;
import com.biletflow.biletflow.promotions.application.promocode.view.PromoCodeView;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignRepository;
import com.biletflow.biletflow.promotions.domain.promocode.CampaignToken;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeId;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final CampaignRepository campaignRepository;
    private final EventManagementPort eventManagementPort;
    private final PromotionAuditService auditService;
    private final CurrentActor currentActor;
    private final Clock clock;

    public PromoCodeService(
        PromoCodeRepository promoCodeRepository,
        CampaignRepository campaignRepository,
        EventManagementPort eventManagementPort,
        PromotionAuditService auditService,
        CurrentActor currentActor,
        Clock clock
    ) {
        this.promoCodeRepository = Objects.requireNonNull(promoCodeRepository);
        this.campaignRepository = Objects.requireNonNull(campaignRepository);
        this.eventManagementPort = Objects.requireNonNull(eventManagementPort);
        this.auditService = Objects.requireNonNull(auditService);
        this.currentActor = Objects.requireNonNull(currentActor);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public PromoCodeView disable(DisablePromoCodeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        PromoCode promoCode = requirePromoCode(command.promoCodeId());
        Campaign campaign = requireCampaign(promoCode.getCampaignId());
        Long actorUserId = authorize(campaign.getEventId());

        promoCode.disable(Instant.now(clock));
        PromoCode saved = promoCodeRepository.save(promoCode);

        auditService.record(
            campaign.getEventId(),
            actorUserId,
            PromotionAuditType.PROMO_CODE_DISABLED,
            "Promo code " + saved.getCode().value() + " disabled"
        );

        return PromoCodeView.from(saved);
    }

    @Transactional
    public PromoCodeView enable(EnablePromoCodeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        PromoCode promoCode = requirePromoCode(command.promoCodeId());
        Campaign campaign = requireCampaign(promoCode.getCampaignId());
        Long actorUserId = authorize(campaign.getEventId());

        promoCode.enable();
        PromoCode saved = promoCodeRepository.save(promoCode);

        auditService.record(
            campaign.getEventId(),
            actorUserId,
            PromotionAuditType.PROMO_CODE_ENABLED,
            "Promo code " + saved.getCode().value() + " enabled"
        );

        return PromoCodeView.from(saved);
    }

    public CampaignLinkView resolveCampaignLink(UUID campaignToken) {
        Objects.requireNonNull(campaignToken, "campaignToken cannot be null");

        PromoCode promoCode = promoCodeRepository
            .findByToken(new CampaignToken(campaignToken))
            .orElseThrow(() -> new EntityNotFoundException("Campaign link not found"));
        Campaign campaign = requireCampaign(promoCode.getCampaignId());

        return new CampaignLinkView(campaign.getEventId(), promoCode.getCode().value());
    }

    private PromoCode requirePromoCode(UUID promoCodeId) {
        Objects.requireNonNull(promoCodeId, "promoCodeId cannot be null");

        return promoCodeRepository
            .findById(new PromoCodeId(promoCodeId))
            .orElseThrow(() -> new EntityNotFoundException("Promo code not found: " + promoCodeId));
    }

    private Campaign requireCampaign(CampaignId campaignId) {
        return campaignRepository
            .findById(campaignId)
            .orElseThrow(() -> new EntityNotFoundException("Campaign not found: " + campaignId.value()));
    }

    private Long authorize(UUID eventId) {
        Long actorUserId = currentActor.requireUserId();

        if (!eventManagementPort.canManagePromotions(eventId, actorUserId)) {
            throw new AuthorizationException("User is not authorized to manage promotions for event: " + eventId);
        }

        return actorUserId;
    }
}
