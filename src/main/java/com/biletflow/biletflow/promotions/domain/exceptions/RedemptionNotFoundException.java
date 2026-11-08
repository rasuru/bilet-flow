package com.biletflow.biletflow.promotions.domain.exceptions;

public class RedemptionNotFoundException extends PromotionsDomainException {
    public RedemptionNotFoundException(String redemptionId) {
        super("Promo redemption not found: " + redemptionId);
    }
}
