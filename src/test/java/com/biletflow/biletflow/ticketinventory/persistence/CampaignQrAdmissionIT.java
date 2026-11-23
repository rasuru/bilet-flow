package com.biletflow.biletflow.ticketinventory.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.biletflow.biletflow.IntegrationTest;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.eventmanagement.ohs.EventManagementOhs;
import com.biletflow.biletflow.eventmanagement.ohs.dto.TicketEventDetailsView;
import com.biletflow.biletflow.promotions.application.campaign.CampaignService;
import com.biletflow.biletflow.promotions.application.campaign.command.CreateCampaignCommand;
import com.biletflow.biletflow.promotions.domain.campaign.DiscountType;
import com.biletflow.biletflow.ticketinventory.application.ticket.CheckInService;
import com.biletflow.biletflow.ticketinventory.application.ticket.TicketNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class CampaignQrAdmissionIT {

    @MockitoBean
    EventManagementOhs events;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    CurrentActor actor;

    @Autowired
    CampaignService campaigns;

    @Autowired
    CheckInService checkIn;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    JdbcTemplate jdbc;

    private final long organizer = ThreadLocalRandom.current().nextLong(100000, Long.MAX_VALUE);
    private final UUID eventId = UUID.randomUUID();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void eventWhereTheOrganizerCanCheckIn() {
        doReturn(Optional.of(organizer)).when(actor).userId();
        when(events.canManageTicketing(eventId, organizer)).thenReturn(true);
        when(events.canCheckIn(eventId, organizer)).thenReturn(true);
        when(events.salesScope(eq(eventId), any())).thenReturn(
            new EventManagementOhs.SalesScope(eventId, organizer, true, false, now.plusSeconds(86_400))
        );
        when(events.ticketDetails(eq(eventId), any())).thenReturn(
            new TicketEventDetailsView("Concert", now.plusSeconds(86_400), now.plusSeconds(97_200), "Hall", "Main street 1", null, false)
        );
    }

    @AfterEach
    void cleanup() {
        String campaignIds = "select id from promotions_campaign where event_id = ?";
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.update("delete from promotions_redemption where campaign_id in (" + campaignIds + ")", eventId);
            jdbc.update("delete from promotions_campaign_ticket_type where campaign_id in (" + campaignIds + ")", eventId);
            jdbc.update("delete from promotions_promo_code where campaign_id in (" + campaignIds + ")", eventId);
            jdbc.update("delete from promotions_campaign where event_id = ?", eventId);
            jdbc.update("delete from promotions_activity_log where event_id = ?", eventId);
            jdbc.update("delete from ticket_inventory_activity_log where event_id = ?", eventId);
        });
    }

    @Test
    void campaignQrTokenIsNeverAcceptedAtAdmission() {
        UUID campaignToken = campaigns
            .create(
                new CreateCampaignCommand(
                    eventId,
                    "Student week",
                    DiscountType.PERCENTAGE,
                    BigDecimal.TEN,
                    now.minusSeconds(60),
                    now.plusSeconds(86_400),
                    Set.of(),
                    5
                )
            )
            .promoCodes()
            .get(0)
            .campaignToken();

        assertThrows(TicketNotFoundException.class, () -> checkIn.validate(eventId, campaignToken));
        assertThrows(TicketNotFoundException.class, () -> checkIn.checkIn(eventId, campaignToken));

        assertEquals(0L, checkIn.counts(eventId).checkedIn());
        Integer admissions = jdbc.queryForObject(
            "select count(*) from ticket_inventory_activity_log where event_id = ?",
            Integer.class,
            eventId
        );
        assertEquals(Integer.valueOf(0), admissions);
    }
}
