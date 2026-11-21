package com.biletflow.biletflow.promotions.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditRecord;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditType;
import com.biletflow.biletflow.promotions.application.redemption.RedemptionService;
import com.biletflow.biletflow.promotions.application.redemption.command.ConfirmRedemptionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.PromotionLine;
import com.biletflow.biletflow.promotions.application.redemption.command.ReservePromotionCommand;
import com.biletflow.biletflow.promotions.application.redemption.view.ReservedPromotionView;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignRepository;
import com.biletflow.biletflow.promotions.domain.campaign.Discount;
import com.biletflow.biletflow.promotions.domain.campaign.RedemptionLimit;
import com.biletflow.biletflow.promotions.domain.campaign.ValidityPeriod;
import com.biletflow.biletflow.promotions.domain.exceptions.PromoCodeRejectedException;
import com.biletflow.biletflow.promotions.domain.exceptions.RejectionReason;
import com.biletflow.biletflow.promotions.domain.promocode.CodeValue;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeRepository;
import com.biletflow.biletflow.promotions.domain.redemption.CampaignRedemptionStats;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemptionRepository;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionId;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionSource;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class PromotionsPersistenceIT {

    @Autowired
    CampaignRepository campaigns;

    @Autowired
    PromoCodeRepository promoCodes;

    @Autowired
    PromoRedemptionRepository redemptions;

    @Autowired
    RedemptionService redemptionService;

    @Autowired
    PromotionAuditRepository audit;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private final UUID eventId = UUID.randomUUID();
    private final UUID vip = UUID.randomUUID();

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    private List<PromotionLine> twoVip() {
        return List.of(new PromotionLine(vip, Money.kzt(10_000), 2));
    }

    private Campaign savedCampaign(RedemptionLimit limit) {
        Campaign campaign = Campaign.create(
            eventId,
            "Student week",
            Discount.percentage(new BigDecimal("12.5")),
            new ValidityPeriod(now.minusSeconds(3_600), now.plusSeconds(86_400)),
            Set.of(vip),
            limit,
            now
        );
        return tx().execute(status -> campaigns.save(campaign));
    }

    private PromoCode savedCode(CampaignId campaignId) {
        PromoCode promoCode = PromoCode.generateFor(campaignId, now);
        return tx().execute(status -> promoCodes.save(promoCode));
    }

    private ReservedPromotionView reserve(String code, UUID checkoutId) {
        return redemptionService.reserve(new ReservePromotionCommand(eventId, checkoutId, code, twoVip(), RedemptionSource.CODE));
    }

    @AfterEach
    void cleanup() {
        String campaignIds = "select id from promotions_campaign where event_id = ?";
        tx().executeWithoutResult(status -> {
            jdbc.update("delete from promotions_redemption where campaign_id in (" + campaignIds + ")", eventId);
            jdbc.update("delete from promotions_campaign_ticket_type where campaign_id in (" + campaignIds + ")", eventId);
            jdbc.update("delete from promotions_promo_code where campaign_id in (" + campaignIds + ")", eventId);
            jdbc.update("delete from promotions_campaign where event_id = ?", eventId);
            jdbc.update("delete from promotions_activity_log where event_id = ?", eventId);
        });
    }

    @Test
    void campaignIsStoredAndLoaded() {
        CampaignId id = savedCampaign(RedemptionLimit.of(5)).getId();

        Campaign loaded = tx().execute(status -> campaigns.findById(id).orElseThrow());
        assertEquals(eventId, loaded.getEventId());
        assertEquals(Discount.percentage(new BigDecimal("12.5")), loaded.getDiscount());
        assertEquals(Set.of(vip), loaded.getApplicableTicketTypeIds());

        Campaign locked = tx().execute(status -> campaigns.findByIdForUpdate(id).orElseThrow());
        assertEquals(id, locked.getId());
        assertEquals(
            1,
            tx()
                .execute(status -> campaigns.findByEventId(eventId))
                .size()
        );
    }

    @Test
    void reservedAndConfirmedRedemptionIsStoredWithAttribution() {
        CampaignId campaignId = savedCampaign(RedemptionLimit.of(5)).getId();
        PromoCode promoCode = savedCode(campaignId);
        UUID checkoutId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        ReservedPromotionView reserved = reserve(promoCode.getCode().value(), checkoutId);
        redemptionService.confirm(new ConfirmRedemptionCommand(reserved.redemptionId(), orderId));

        PromoRedemption stored = tx().execute(status -> redemptions.findById(new RedemptionId(reserved.redemptionId())).orElseThrow());
        assertEquals(RedemptionStatus.CONFIRMED, stored.getStatus());
        assertEquals(orderId, stored.getOrderId());
        assertEquals(promoCode.getId(), stored.getPromoCodeId());
        assertEquals(RedemptionSource.CODE, stored.getSource());
        assertEquals(2, stored.getTicketQuantity());
        assertEquals(Money.kzt(20_000), stored.getGrossAmount());
        assertEquals(Money.kzt(2_500), stored.getDiscountAmount());

        Map<CampaignId, CampaignRedemptionStats> stats = tx().execute(status -> redemptions.statsFor(List.of(campaignId), Instant.now()));
        CampaignRedemptionStats campaignStats = stats.get(campaignId);
        assertEquals(1, campaignStats.usedRedemptions());
        assertEquals(1, campaignStats.confirmedRedemptions());
        assertEquals(2, campaignStats.ticketsSold());
        assertEquals(Money.kzt(20_000), campaignStats.grossRevenue());
        assertEquals(Money.kzt(2_500), campaignStats.discountGiven());
        assertEquals(Money.kzt(17_500), campaignStats.netRevenue());
    }

    @Test
    void activeReservationsAreCountedAndReplacedPerCheckout() {
        CampaignId campaignId = savedCampaign(RedemptionLimit.of(5)).getId();
        String code = savedCode(campaignId).getCode().value();
        UUID checkoutId = UUID.randomUUID();

        ReservedPromotionView first = reserve(code, checkoutId);
        reserve(code, checkoutId);
        reserve(code, UUID.randomUUID());

        Integer active = tx().execute(status -> redemptions.countActive(campaignId, Instant.now()));
        assertEquals(Integer.valueOf(2), active);
        PromoRedemption replaced = tx().execute(status -> redemptions.findById(new RedemptionId(first.redemptionId())).orElseThrow());
        assertEquals(RedemptionStatus.RELEASED, replaced.getStatus());
        assertEquals(
            1,
            tx()
                .execute(status -> redemptions.findActiveReservations(campaignId, checkoutId, Instant.now()))
                .size()
        );

        CampaignRedemptionStats stats = tx()
            .execute(status -> redemptions.statsFor(List.of(campaignId), Instant.now()))
            .get(campaignId);
        assertEquals(2, stats.usedRedemptions());
        assertEquals(0, stats.confirmedRedemptions());
    }

    @Test
    void concurrentCheckoutsCannotExceedTheRedemptionLimit() throws Exception {
        CampaignId campaignId = savedCampaign(RedemptionLimit.of(1)).getId();
        String code = savedCode(campaignId).getCode().value();
        int attempts = 4;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<RejectionReason>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(
                    executor.submit(() -> {
                        start.await();
                        try {
                            reserve(code, UUID.randomUUID());
                            return null;
                        } catch (PromoCodeRejectedException exception) {
                            return exception.getReason();
                        }
                    })
                );
            }
            start.countDown();

            int succeeded = 0;
            for (Future<RejectionReason> result : results) {
                RejectionReason reason = result.get(30, TimeUnit.SECONDS);
                if (reason == null) {
                    succeeded++;
                } else {
                    assertEquals(RejectionReason.EXHAUSTED, reason);
                }
            }

            assertEquals(1, succeeded);
            Integer active = tx().execute(status -> redemptions.countActive(campaignId, Instant.now()));
            assertEquals(Integer.valueOf(1), active);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void promoCodeLookupsWork() {
        CampaignId campaignId = savedCampaign(RedemptionLimit.unlimited()).getId();
        PromoCode promoCode = savedCode(campaignId);

        Boolean exists = tx().execute(status -> promoCodes.existsByCode(promoCode.getCode()));
        assertEquals(Boolean.TRUE, exists);
        assertEquals(promoCode.getId(), tx().execute(status -> promoCodes.findByCode(promoCode.getCode()).orElseThrow().getId()));
        assertEquals(promoCode.getId(), tx().execute(status -> promoCodes.findByToken(promoCode.getToken()).orElseThrow().getId()));
        assertEquals(
            1,
            tx()
                .execute(status -> promoCodes.findByCampaignId(campaignId))
                .size()
        );
        assertEquals(
            1,
            tx()
                .execute(status -> promoCodes.findByCampaignIds(List.of(campaignId)))
                .size()
        );

        tx().executeWithoutResult(status -> {
            PromoCode loaded = promoCodes.findById(promoCode.getId()).orElseThrow();
            loaded.disable(now);
            promoCodes.save(loaded);
        });

        PromoCode disabled = tx().execute(status -> promoCodes.findById(promoCode.getId()).orElseThrow());
        assertFalse(disabled.isEnabled());
        assertEquals(now, disabled.getDisabledAt());
    }

    @Test
    void duplicateCodesAreRejectedByTheDatabase() {
        CampaignId campaignId = savedCampaign(RedemptionLimit.unlimited()).getId();
        CodeValue code = CodeValue.generate();
        tx().executeWithoutResult(status -> promoCodes.save(PromoCode.create(campaignId, code, now)));

        assertThrows(DataIntegrityViolationException.class, () ->
            tx().executeWithoutResult(status -> promoCodes.save(PromoCode.create(campaignId, code, now)))
        );
    }

    @Test
    void auditEntriesAreStored() {
        tx().executeWithoutResult(status ->
            audit.append(new PromotionAuditRecord(UUID.randomUUID(), eventId, now, 7L, PromotionAuditType.CAMPAIGN_CREATED, "created"))
        );

        Integer count = jdbc.queryForObject(
            "select count(*) from promotions_activity_log where event_id = ? and type = 'CAMPAIGN_CREATED'",
            Integer.class,
            eventId
        );
        assertEquals(1, count);
    }
}
