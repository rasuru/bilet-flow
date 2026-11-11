package com.biletflow.biletflow.promotions.application.campaign;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditType;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditService;
import com.biletflow.biletflow.promotions.application.campaign.command.CreateCampaignCommand;
import com.biletflow.biletflow.promotions.application.campaign.command.EndCampaignCommand;
import com.biletflow.biletflow.promotions.application.campaign.view.CampaignView;
import com.biletflow.biletflow.promotions.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.promotions.application.ticketinventory.port.TicketInventoryPort;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignRepository;
import com.biletflow.biletflow.promotions.domain.campaign.Discount;
import com.biletflow.biletflow.promotions.domain.campaign.RedemptionLimit;
import com.biletflow.biletflow.promotions.domain.campaign.ValidityPeriod;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import com.biletflow.biletflow.promotions.domain.promocode.CodeValue;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CampaignService {

    private static final int CODE_GENERATION_ATTEMPTS = 5;

    private final CampaignRepository campaignRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final EventManagementPort eventManagementPort;
    private final TicketInventoryPort ticketInventoryPort;
    private final PromotionAuditService auditService;
    private final CurrentActor currentActor;
    private final Clock clock;

    public CampaignService(
        CampaignRepository campaignRepository,
        PromoCodeRepository promoCodeRepository,
        EventManagementPort eventManagementPort,
        TicketInventoryPort ticketInventoryPort,
        PromotionAuditService auditService,
        CurrentActor currentActor,
        Clock clock
    ) {
        this.campaignRepository = Objects.requireNonNull(campaignRepository);
        this.promoCodeRepository = Objects.requireNonNull(promoCodeRepository);
        this.eventManagementPort = Objects.requireNonNull(eventManagementPort);
        this.ticketInventoryPort = Objects.requireNonNull(ticketInventoryPort);
        this.auditService = Objects.requireNonNull(auditService);
        this.currentActor = Objects.requireNonNull(currentActor);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public CampaignView create(CreateCampaignCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(command.eventId(), "eventId cannot be null");

        Long actorUserId = authorize(command.eventId());

        if (eventManagementPort.isCancelled(command.eventId())) {
            throw new InvalidCampaignStateException("Cannot create a campaign for a cancelled event");
        }

        ensureTicketTypesBelongToEvent(command.eventId(), command.applicableTicketTypeIds());

        Instant now = Instant.now(clock);

        Campaign campaign = Campaign.create(
            command.eventId(),
            command.name(),
            toDiscount(command),
            new ValidityPeriod(command.validFrom(), command.validUntil()),
            command.applicableTicketTypeIds(),
            command.maxRedemptions() == null ? RedemptionLimit.unlimited() : RedemptionLimit.of(command.maxRedemptions()),
            now
        );

        Campaign saved = campaignRepository.save(campaign);
        PromoCode promoCode = promoCodeRepository.save(generateUniquePromoCode(saved.getId(), now));

        auditService.record(
            saved.getEventId(),
            actorUserId,
            PromotionAuditType.CAMPAIGN_CREATED,
            "Campaign \"" + saved.getName() + "\" created"
        );
        auditService.record(
            saved.getEventId(),
            actorUserId,
            PromotionAuditType.PROMO_CODE_CREATED,
            "Promo code " + promoCode.getCode().value() + " created for campaign \"" + saved.getName() + "\""
        );

        return CampaignView.from(saved, List.of(promoCode), now);
    }

    @Transactional
    public CampaignView end(EndCampaignCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Campaign campaign = requireCampaign(command.campaignId());
        Long actorUserId = authorize(campaign.getEventId());
        Instant now = Instant.now(clock);

        campaign.end(now);
        Campaign saved = campaignRepository.save(campaign);

        auditService.record(
            saved.getEventId(),
            actorUserId,
            PromotionAuditType.CAMPAIGN_ENDED,
            "Campaign \"" + saved.getName() + "\" ended"
        );

        return CampaignView.from(saved, promoCodeRepository.findByCampaignId(saved.getId()), now);
    }

    public CampaignView get(UUID campaignId) {
        Campaign campaign = requireCampaign(campaignId);
        authorize(campaign.getEventId());

        return CampaignView.from(campaign, promoCodeRepository.findByCampaignId(campaign.getId()), Instant.now(clock));
    }

    public List<CampaignView> listForEvent(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        authorize(eventId);

        Instant now = Instant.now(clock);
        return campaignRepository
            .findByEventId(eventId)
            .stream()
            .map(campaign -> CampaignView.from(campaign, promoCodeRepository.findByCampaignId(campaign.getId()), now))
            .toList();
    }

    private Discount toDiscount(CreateCampaignCommand command) {
        Objects.requireNonNull(command.discountType(), "discountType cannot be null");
        Objects.requireNonNull(command.discountValue(), "discountValue cannot be null");

        return switch (command.discountType()) {
            case PERCENTAGE -> Discount.percentage(command.discountValue());
            case FIXED_KZT -> Discount.fixedKzt(Money.kzt(command.discountValue()));
        };
    }

    private void ensureTicketTypesBelongToEvent(UUID eventId, Set<UUID> ticketTypeIds) {
        if (ticketTypeIds.isEmpty()) {
            return;
        }

        Set<UUID> eventTicketTypeIds = ticketInventoryPort.ticketTypeIdsOf(eventId);
        for (UUID ticketTypeId : ticketTypeIds) {
            if (!eventTicketTypeIds.contains(ticketTypeId)) {
                throw new IllegalArgumentException("Ticket type does not belong to event " + eventId + ": " + ticketTypeId);
            }
        }
    }

    private PromoCode generateUniquePromoCode(CampaignId campaignId, Instant now) {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            CodeValue code = CodeValue.generate();
            if (!promoCodeRepository.existsByCode(code)) {
                return PromoCode.create(campaignId, code, now);
            }
        }

        throw new IllegalStateException("Could not generate a unique promo code");
    }

    private Campaign requireCampaign(UUID campaignId) {
        Objects.requireNonNull(campaignId, "campaignId cannot be null");

        return campaignRepository
            .findById(new CampaignId(campaignId))
            .orElseThrow(() -> new EntityNotFoundException("Campaign not found: " + campaignId));
    }

    private Long authorize(UUID eventId) {
        Long actorUserId = currentActor.requireUserId();

        if (!eventManagementPort.canManagePromotions(eventId, actorUserId)) {
            throw new AuthorizationException("User is not authorized to manage promotions for event: " + eventId);
        }

        return actorUserId;
    }
}
