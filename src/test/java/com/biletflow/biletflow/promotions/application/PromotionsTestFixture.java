package com.biletflow.biletflow.promotions.application;

import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditService;
import com.biletflow.biletflow.promotions.application.campaign.CampaignService;
import com.biletflow.biletflow.promotions.application.campaign.command.CreateCampaignCommand;
import com.biletflow.biletflow.promotions.application.campaign.view.CampaignView;
import com.biletflow.biletflow.promotions.application.eventmanagement.port.EventManagementPort;
import com.biletflow.biletflow.promotions.application.promocode.PromoCodeService;
import com.biletflow.biletflow.promotions.application.redemption.RedemptionService;
import com.biletflow.biletflow.promotions.application.ticketinventory.port.TicketInventoryPort;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignRepository;
import com.biletflow.biletflow.promotions.domain.campaign.DiscountType;
import com.biletflow.biletflow.promotions.domain.promocode.CampaignToken;
import com.biletflow.biletflow.promotions.domain.promocode.CodeValue;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeId;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeRepository;
import com.biletflow.biletflow.promotions.domain.redemption.CampaignRedemptionStats;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemptionRepository;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import com.biletflow.biletflow.promotions.domain.redemption.ReservationLifetime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PromotionsTestFixture {

    public static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    public static final Duration LIFETIME = Duration.ofMinutes(15);
    public static final Long ORGANIZER = 7L;
    public static final Long STRANGER = 99L;

    public final UUID eventId = UUID.randomUUID();
    public final UUID vip = UUID.randomUUID();
    public final UUID standard = UUID.randomUUID();

    public final InMemoryCampaignRepository campaigns = new InMemoryCampaignRepository();
    public final InMemoryPromoCodeRepository promoCodes = new InMemoryPromoCodeRepository();
    public final InMemoryRedemptionRepository redemptions = new InMemoryRedemptionRepository();
    public final FakeEventManagement eventManagement = new FakeEventManagement();
    public final FakeTicketInventory ticketInventory = new FakeTicketInventory();
    public final RecordingAuditRepository audit = new RecordingAuditRepository();
    public final MutableClock clock = new MutableClock(NOW);
    public Long currentUserId = ORGANIZER;

    private final CurrentActor currentActor = () -> Optional.ofNullable(currentUserId);
    private final PromotionAuditService auditService = new PromotionAuditService(audit, clock);

    public final CampaignService campaignService = new CampaignService(
        campaigns,
        promoCodes,
        redemptions,
        eventManagement,
        ticketInventory,
        auditService,
        currentActor,
        clock
    );
    public final PromoCodeService promoCodeService = new PromoCodeService(
        promoCodes,
        campaigns,
        redemptions,
        eventManagement,
        auditService,
        currentActor,
        clock
    );
    public final RedemptionService redemptionService = new RedemptionService(
        campaigns,
        promoCodes,
        redemptions,
        new ReservationLifetime(LIFETIME),
        clock
    );

    public PromotionsTestFixture() {
        eventManagement.managers.put(eventId, ORGANIZER);
        ticketInventory.ticketTypes.put(eventId, Set.of(vip, standard));
    }

    public CreateCampaignCommand tenPercentOnVip(Integer maxRedemptions) {
        return new CreateCampaignCommand(
            eventId,
            "Student week",
            DiscountType.PERCENTAGE,
            BigDecimal.TEN,
            NOW.minusSeconds(3_600),
            NOW.plusSeconds(86_400),
            Set.of(vip),
            maxRedemptions
        );
    }

    public CampaignView createTenPercentOnVip(Integer maxRedemptions) {
        return campaignService.create(tenPercentOnVip(maxRedemptions));
    }

    public static class MutableClock extends Clock {

        private Instant now;

        public MutableClock(Instant now) {
            this.now = now;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    public static class InMemoryCampaignRepository implements CampaignRepository {

        public final Map<CampaignId, Campaign> stored = new LinkedHashMap<>();
        public int lockedLoads;

        @Override
        public Optional<Campaign> findById(CampaignId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<Campaign> findByIdForUpdate(CampaignId id) {
            lockedLoads++;
            return findById(id);
        }

        @Override
        public List<Campaign> findByEventId(UUID eventId) {
            return stored
                .values()
                .stream()
                .filter(campaign -> campaign.getEventId().equals(eventId))
                .toList();
        }

        @Override
        public Campaign save(Campaign campaign) {
            stored.put(campaign.getId(), campaign);
            return campaign;
        }
    }

    public static class InMemoryPromoCodeRepository implements PromoCodeRepository {

        public final Map<PromoCodeId, PromoCode> stored = new LinkedHashMap<>();
        public int batchLookups;

        @Override
        public Optional<PromoCode> findById(PromoCodeId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<PromoCode> findByCode(CodeValue code) {
            return stored
                .values()
                .stream()
                .filter(promoCode -> promoCode.getCode().equals(code))
                .findFirst();
        }

        @Override
        public Optional<PromoCode> findByToken(CampaignToken token) {
            return stored
                .values()
                .stream()
                .filter(promoCode -> promoCode.getToken().equals(token))
                .findFirst();
        }

        @Override
        public List<PromoCode> findByCampaignId(CampaignId campaignId) {
            return stored
                .values()
                .stream()
                .filter(promoCode -> promoCode.getCampaignId().equals(campaignId))
                .toList();
        }

        @Override
        public List<PromoCode> findByCampaignIds(Collection<CampaignId> campaignIds) {
            batchLookups++;
            return stored
                .values()
                .stream()
                .filter(promoCode -> campaignIds.contains(promoCode.getCampaignId()))
                .toList();
        }

        @Override
        public boolean existsByCode(CodeValue code) {
            return findByCode(code).isPresent();
        }

        @Override
        public PromoCode save(PromoCode promoCode) {
            stored.put(promoCode.getId(), promoCode);
            return promoCode;
        }
    }

    public static class InMemoryRedemptionRepository implements PromoRedemptionRepository {

        public final Map<RedemptionId, PromoRedemption> stored = new LinkedHashMap<>();

        @Override
        public Optional<PromoRedemption> findById(RedemptionId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<PromoRedemption> findActiveReservations(CampaignId campaignId, UUID sessionId, Instant now) {
            return stored
                .values()
                .stream()
                .filter(r -> r.getCampaignId().equals(campaignId) && r.getSessionId().equals(sessionId))
                .filter(r -> r.getStatus() == RedemptionStatus.RESERVED && !r.isExpiredAt(now))
                .toList();
        }

        @Override
        public int countActive(CampaignId campaignId, Instant now) {
            return (int) stored
                .values()
                .stream()
                .filter(r -> r.getCampaignId().equals(campaignId) && r.countsTowardsLimitAt(now))
                .count();
        }

        @Override
        public Map<CampaignId, CampaignRedemptionStats> statsFor(Collection<CampaignId> campaignIds, Instant now) {
            Map<CampaignId, CampaignRedemptionStats> stats = new HashMap<>();
            for (CampaignId id : campaignIds) {
                List<PromoRedemption> confirmed = stored
                    .values()
                    .stream()
                    .filter(r -> r.getCampaignId().equals(id) && r.getStatus() == RedemptionStatus.CONFIRMED)
                    .toList();
                int used = countActive(id, now);
                if (used == 0 && confirmed.isEmpty()) {
                    continue;
                }
                stats.put(
                    id,
                    new CampaignRedemptionStats(
                        used,
                        confirmed.size(),
                        confirmed.stream().mapToInt(PromoRedemption::getTicketQuantity).sum(),
                        confirmed.stream().map(PromoRedemption::getGrossAmount).reduce(Money.zeroKzt(), Money::add),
                        confirmed.stream().map(PromoRedemption::getDiscountAmount).reduce(Money.zeroKzt(), Money::add)
                    )
                );
            }
            return stats;
        }

        @Override
        public PromoRedemption save(PromoRedemption redemption) {
            stored.put(redemption.getId(), redemption);
            return redemption;
        }
    }

    public static class FakeEventManagement implements EventManagementPort {

        public final Map<UUID, Long> managers = new LinkedHashMap<>();
        public final Set<UUID> cancelledEvents = new HashSet<>();

        @Override
        public boolean canManagePromotions(UUID eventId, Long userId) {
            return userId.equals(managers.get(eventId));
        }

        @Override
        public boolean isCancelled(UUID eventId) {
            return cancelledEvents.contains(eventId);
        }
    }

    public static class FakeTicketInventory implements TicketInventoryPort {

        public final Map<UUID, Set<UUID>> ticketTypes = new LinkedHashMap<>();

        @Override
        public Set<UUID> ticketTypeIdsOf(UUID eventId) {
            return ticketTypes.getOrDefault(eventId, Set.of());
        }
    }

    public static class RecordingAuditRepository implements PromotionAuditRepository {

        public final List<PromotionAuditRecord> records = new ArrayList<>();

        @Override
        public void append(PromotionAuditRecord record) {
            records.add(record);
        }

        public List<PromotionAuditType> types() {
            return records.stream().map(PromotionAuditRecord::type).toList();
        }
    }
}
