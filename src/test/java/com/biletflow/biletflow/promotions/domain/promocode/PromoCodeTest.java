package com.biletflow.biletflow.promotions.domain.promocode;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.exceptions.InvalidCampaignStateException;
import com.biletflow.biletflow.promotions.domain.exceptions.PromoCodeRejectedException;
import com.biletflow.biletflow.promotions.domain.exceptions.RejectionReason;
import java.time.Instant;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class PromoCodeTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void generatedPromoCodeIsEnabledAndHasACodeAndQrToken() {
        CampaignId campaignId = CampaignId.generate();

        PromoCode promoCode = PromoCode.generateFor(campaignId, NOW);

        assertEquals(campaignId, promoCode.getCampaignId());
        assertNotNull(promoCode.getCode());
        assertNotNull(promoCode.getToken());
        assertTrue(promoCode.isEnabled());
        promoCode.ensureRedeemable();
    }

    @Test
    void disabledPromoCodeIsRejectedUntilEnabledAgain() {
        PromoCode promoCode = PromoCode.generateFor(CampaignId.generate(), NOW);

        promoCode.disable(NOW);

        assertFalse(promoCode.isEnabled());
        assertEquals(NOW, promoCode.getDisabledAt());
        assertEquals(RejectionReason.DISABLED, assertThrows(PromoCodeRejectedException.class, promoCode::ensureRedeemable).getReason());

        promoCode.enable();

        assertTrue(promoCode.isEnabled());
        assertNull(promoCode.getDisabledAt());
        promoCode.ensureRedeemable();
    }

    @Test
    void disablingTwiceOrEnablingAnEnabledCodeIsRejected() {
        PromoCode promoCode = PromoCode.generateFor(CampaignId.generate(), NOW);

        assertThrows(InvalidCampaignStateException.class, promoCode::enable);
        promoCode.disable(NOW);
        assertThrows(InvalidCampaignStateException.class, () -> promoCode.disable(NOW));
    }

    @Test
    void codeValueIsNormalizedToUppercase() {
        assertEquals("SPRING2026", new CodeValue("  spring2026 ").value());
    }

    @Test
    void codeValueMustBeSixToTwentyLettersOrDigits() {
        assertThrows(IllegalArgumentException.class, () -> new CodeValue("ABC12"));
        assertThrows(IllegalArgumentException.class, () -> new CodeValue("A".repeat(21)));
        assertThrows(IllegalArgumentException.class, () -> new CodeValue("SPRING-26"));
    }

    @Test
    void generatedCodesAvoidAmbiguousCharacters() {
        SplittableRandom random = new SplittableRandom(42);

        for (int i = 0; i < 1_000; i++) {
            String code = CodeValue.generate(random).value();
            assertEquals(8, code.length());
            assertTrue(code.chars().noneMatch(c -> "01OIL".indexOf(c) >= 0), code);
        }
    }
}
