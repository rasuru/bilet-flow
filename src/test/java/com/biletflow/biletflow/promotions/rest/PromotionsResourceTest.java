package com.biletflow.biletflow.promotions.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.biletflow.biletflow.promotions.application.PromotionsTestFixture;
import com.biletflow.biletflow.promotions.application.campaign.view.CampaignView;
import com.biletflow.biletflow.promotions.application.promocode.view.PromoCodeView;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PromotionsResourceTest {

    private final PromotionsTestFixture fixture = new PromotionsTestFixture();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
        new CampaignResource(fixture.campaignService),
        new PromoCodeResource(fixture.promoCodeService)
    )
        .setControllerAdvice(new PromotionsExceptionHandler())
        .build();

    private String campaignJson(String name) {
        return """
        {
          "name": "%s",
          "discountType": "PERCENTAGE",
          "discountValue": 10,
          "validFrom": "2026-11-12T09:00:00Z",
          "validUntil": "2026-11-13T10:00:00Z",
          "applicableTicketTypeIds": ["%s"],
          "maxRedemptions": 100
        }
        """.formatted(name, fixture.vip);
    }

    @Test
    void organizerCreatesACampaign() throws Exception {
        mvc.perform(
            post("/api/events/{eventId}/campaigns", fixture.eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(campaignJson("Student week"))
        )
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.name").value("Student week"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.remainingRedemptions").value(100))
            .andExpect(jsonPath("$.promoCodes[0].code").isNotEmpty())
            .andExpect(jsonPath("$.promoCodes[0].campaignToken").isNotEmpty());
    }

    @Test
    void invalidCampaignRequestIsABadRequest() throws Exception {
        mvc.perform(
            post("/api/events/{eventId}/campaigns", fixture.eventId).contentType(MediaType.APPLICATION_JSON).content(campaignJson(" "))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void userWhoCannotManageTheEventIsForbidden() throws Exception {
        fixture.currentUserId = PromotionsTestFixture.STRANGER;

        mvc.perform(get("/api/events/{eventId}/campaigns", fixture.eventId)).andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserIsUnauthorizedForManagement() throws Exception {
        fixture.currentUserId = null;

        mvc.perform(get("/api/events/{eventId}/campaigns", fixture.eventId)).andExpect(status().isUnauthorized());
    }

    @Test
    void endingACampaignTwiceIsAConflict() throws Exception {
        CampaignView campaign = fixture.createTenPercentOnVip(10);

        mvc.perform(post("/api/campaigns/{campaignId}/end", campaign.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ENDED"));
        mvc.perform(post("/api/campaigns/{campaignId}/end", campaign.id()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Campaign has already ended"));
    }

    @Test
    void promoCodeCanBeDisabledAndEnabled() throws Exception {
        PromoCodeView promoCode = fixture.createTenPercentOnVip(10).promoCodes().get(0);

        mvc.perform(post("/api/promo-codes/{promoCodeId}/disable", promoCode.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/promo-codes/{promoCodeId}/enable", promoCode.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void campaignLinkResolvesWithoutAuthentication() throws Exception {
        PromoCodeView promoCode = fixture.createTenPercentOnVip(10).promoCodes().get(0);
        fixture.currentUserId = null;

        mvc.perform(get("/api/campaign-links/{token}", promoCode.campaignToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value(fixture.eventId.toString()))
            .andExpect(jsonPath("$.code").value(promoCode.code()));
    }

    @Test
    void campaignLinkOfADisabledCodeIsARejectedProblem() throws Exception {
        PromoCodeView promoCode = fixture.createTenPercentOnVip(10).promoCodes().get(0);
        mvc.perform(post("/api/promo-codes/{promoCodeId}/disable", promoCode.id())).andExpect(status().isOk());

        mvc.perform(get("/api/campaign-links/{token}", promoCode.campaignToken()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#promo-code-rejected"))
            .andExpect(jsonPath("$.reason").value("DISABLED"));
    }

    @Test
    void unknownCampaignLinkIsNotFound() throws Exception {
        mvc.perform(get("/api/campaign-links/{token}", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
