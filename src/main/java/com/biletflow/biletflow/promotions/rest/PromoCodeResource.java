package com.biletflow.biletflow.promotions.rest;

import com.biletflow.biletflow.promotions.application.promocode.PromoCodeService;
import com.biletflow.biletflow.promotions.application.promocode.command.DisablePromoCodeCommand;
import com.biletflow.biletflow.promotions.application.promocode.command.EnablePromoCodeCommand;
import com.biletflow.biletflow.promotions.application.promocode.view.CampaignLinkView;
import com.biletflow.biletflow.promotions.application.promocode.view.PromoCodeView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Promo Codes", description = "Promo-code state and public Campaign QR link resolution.")
public class PromoCodeResource {

    private final PromoCodeService promoCodeService;

    public PromoCodeResource(PromoCodeService promoCodeService) {
        this.promoCodeService = Objects.requireNonNull(promoCodeService, "promoCodeService cannot be null");
    }

    @PostMapping("/promo-codes/{promoCodeId}/disable")
    @Operation(summary = "Disable a promo code")
    public PromoCodeView disable(@PathVariable UUID promoCodeId) {
        return promoCodeService.disable(new DisablePromoCodeCommand(promoCodeId));
    }

    @PostMapping("/promo-codes/{promoCodeId}/enable")
    @Operation(summary = "Enable a disabled promo code")
    public PromoCodeView enable(@PathVariable UUID promoCodeId) {
        return promoCodeService.enable(new EnablePromoCodeCommand(promoCodeId));
    }

    @GetMapping("/campaign-links/{campaignToken}")
    @Operation(summary = "Resolve a Campaign QR token to its event and promo code")
    public CampaignLinkView resolveCampaignLink(@PathVariable UUID campaignToken) {
        return promoCodeService.resolveCampaignLink(campaignToken);
    }
}
