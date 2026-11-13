package com.biletflow.biletflow.promotions.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.domain.Money;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditRecord;
import com.biletflow.biletflow.promotions.application.audit.PromotionAuditRepository.PromotionAuditType;
import com.biletflow.biletflow.promotions.domain.campaign.Campaign;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.campaign.CampaignRepository;
import com.biletflow.biletflow.promotions.domain.campaign.CheckoutLine;
import com.biletflow.biletflow.promotions.domain.campaign.Discount;
import com.biletflow.biletflow.promotions.domain.campaign.RedemptionLimit;
import com.biletflow.biletflow.promotions.domain.campaign.ValidityPeriod;
import com.biletflow.biletflow.promotions.domain.promocode.CodeValue;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeRepository;
import com.biletflow.biletflow.promotions.domain.redemption.PromoRedemption;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class PromotionsPersistenceIT {

    @Autowired
    CampaignRepository campaigns;

    @Autowired
    PromoCodeRepository promoCodes;

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

    private TransactionTemplate newTx() {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private List<CheckoutLine> oneVip() {
        return List.of(new CheckoutLine(vip, Money.kzt(10_000), 1));
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

    private Campaign load(CampaignId id) {
        return tx().execute(status -> campaigns.findById(id).orElseThrow());
    }

    @AfterEach
    void cleanup() {
        String campaignIds = "select id from promotions_campaign where event_id = ?";
        jdbc.update("delete from promotions_campaign_redemption where campaign_id in (" + campaignIds + ")", eventId);
        jdbc.update("delete from promotions_campaign_ticket_type where campaign_id in (" + campaignIds + ")", eventId);
        jdbc.update("delete from promotions_promo_code where campaign_id in (" + campaignIds + ")", eventId);
        jdbc.update("delete from promotions_campaign where event_id = ?", eventId);
        jdbc.update("delete from promotions_activity_log where event_id = ?", eventId);
    }

    @Test
    void campaignWithRedemptionsIsStoredAndLoaded() {
        CampaignId id = savedCampaign(RedemptionLimit.of(5)).getId();
        UUID orderId = UUID.randomUUID();

        PromoRedemption reserved = tx().execute(status -> {
            Campaign campaign = campaigns.findById(id).orElseThrow();
            PromoRedemption redemption = campaign.reserveRedemption(UUID.randomUUID(), oneVip(), now, now.plusSeconds(600));
            campaign.confirmRedemption(redemption.getId(), orderId, now);
            campaigns.save(campaign);
            return redemption;
        });

        Campaign loaded = load(id);
        assertEquals(eventId, loaded.getEventId());
        assertEquals(Discount.percentage(new BigDecimal("12.5")), loaded.getDiscount());
        assertEquals(Set.of(vip), loaded.getApplicableTicketTypeIds());
        assertEquals(1, loaded.getRedemptions().size());

        PromoRedemption stored = loaded.getRedemptions().get(0);
        assertEquals(RedemptionStatus.CONFIRMED, stored.getStatus());
        assertEquals(orderId, stored.getOrderId());
        assertEquals(Money.kzt(1_250), stored.getDiscountAmount());
        assertEquals(4, loaded.remainingRedemptionsAt(now));

        Campaign byRedemption = tx().execute(status -> campaigns.findByRedemptionId(reserved.getId()).orElseThrow());
        assertEquals(id, byRedemption.getId());

        List<Campaign> forEvent = tx().execute(status -> campaigns.findByEventId(eventId));
        assertEquals(1, forEvent.size());
    }

    @Test
    void concurrentCheckoutsCannotBothTakeTheLastRedemption() {
        CampaignId id = savedCampaign(RedemptionLimit.of(1)).getId();

        assertThrows(ConcurrencyFailureException.class, () ->
            tx().executeWithoutResult(outer -> {
                Campaign first = campaigns.findById(id).orElseThrow();

                newTx().executeWithoutResult(inner -> {
                    Campaign second = campaigns.findById(id).orElseThrow();
                    second.reserveRedemption(UUID.randomUUID(), oneVip(), now, now.plusSeconds(600));
                    campaigns.save(second);
                });

                first.reserveRedemption(UUID.randomUUID(), oneVip(), now, now.plusSeconds(600));
                campaigns.save(first);
            })
        );

        assertEquals(1, load(id).getRedemptions().size());
    }

    @Test
    void promoCodeLookupsWork() {
        CampaignId campaignId = savedCampaign(RedemptionLimit.unlimited()).getId();
        PromoCode promoCode = PromoCode.generateFor(campaignId, now);
        tx().executeWithoutResult(status -> promoCodes.save(promoCode));

        Boolean exists = tx().execute(status -> promoCodes.existsByCode(promoCode.getCode()));
        assertEquals(Boolean.TRUE, exists);
        assertEquals(promoCode.getId(), tx().execute(status -> promoCodes.findByCode(promoCode.getCode()).orElseThrow().getId()));
        assertEquals(promoCode.getId(), tx().execute(status -> promoCodes.findByToken(promoCode.getToken()).orElseThrow().getId()));
        assertEquals(1, tx().execute(status -> promoCodes.findByCampaignId(campaignId)).size());

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
