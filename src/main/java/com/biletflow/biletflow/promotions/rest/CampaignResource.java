package com.biletflow.biletflow.promotions.rest;

import com.biletflow.biletflow.promotions.application.campaign.CampaignService;
import com.biletflow.biletflow.promotions.application.campaign.command.CreateCampaignCommand;
import com.biletflow.biletflow.promotions.application.campaign.command.EndCampaignCommand;
import com.biletflow.biletflow.promotions.application.campaign.view.CampaignView;
import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.promotions.infrastructure.CampaignShare;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Campaigns", description = "Organizer management of promotional campaigns.")
public class CampaignResource {

    private final CampaignService campaignService;

    private final CampaignShare campaignShare;

    public CampaignResource(CampaignService campaignService, CampaignShare campaignShare) {
        this.campaignService = Objects.requireNonNull(campaignService, "campaignService cannot be null");
        this.campaignShare = Objects.requireNonNull(campaignShare, "campaignShare cannot be null");
    }

    @PostMapping("/events/{eventId}/campaigns")
    @Operation(summary = "Create a campaign with its promo code and Campaign QR token")
    public ResponseEntity<CampaignView> create(@PathVariable UUID eventId, @Valid @RequestBody CreateCampaignRequest request) {
        CampaignView campaign = campaignService.create(
            new CreateCampaignCommand(
                eventId,
                request.name(),
                request.discountType(),
                request.discountValue(),
                request.validFrom(),
                request.validUntil(),
                request.applicableTicketTypeIds(),
                request.maxRedemptions()
            )
        );

        return ResponseEntity.created(URI.create("/api/campaigns/" + campaign.id())).body(campaign);
    }

    @GetMapping("/events/{eventId}/campaigns")
    @Operation(summary = "List the campaigns of an event")
    public List<CampaignView> listForEvent(@PathVariable UUID eventId) {
        return campaignService.listForEvent(eventId);
    }

    @GetMapping("/campaigns/{campaignId}")
    @Operation(summary = "Get a campaign with its redemption totals")
    public CampaignView get(@PathVariable UUID campaignId) {
        return campaignService.get(campaignId);
    }

    @GetMapping(value = "/campaigns/{campaignId}/promo-codes/{promoCodeId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Download the Campaign QR image for a promo code")
    public byte[] qr(@PathVariable UUID campaignId, @PathVariable UUID promoCodeId) {
        CampaignView campaign = campaignService.get(campaignId);
        var code = campaign.promoCodes().stream()
            .filter(promo -> promo.id().equals(promoCodeId))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Promo code not found for this campaign"));
        return campaignShare.qr(campaignShare.link(campaign.eventId(), code.campaignToken()));
    }

    @PostMapping("/campaigns/{campaignId}/end")
    @Operation(summary = "End a campaign early")
    public CampaignView end(@PathVariable UUID campaignId) {
        return campaignService.end(new EndCampaignCommand(campaignId));
    }
}
